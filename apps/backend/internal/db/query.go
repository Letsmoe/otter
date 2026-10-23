package db

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"reflect"
	"regexp"
	"strings"
	"time"
)

// Row is one result row keyed by column name. JSON columns are decoded,
// BOOLEAN columns are bool, NULL is absent.
type Row = map[string]any

// Vars binds named $parameters in a query.
type Vars = map[string]any

// Query runs a SELECT and returns every row.
func (d *DB) Query(ctx context.Context, query string, vars Vars) ([]Row, error) {
	rows, err := d.executor.QueryContext(ctx, query, namedArgs(query, vars)...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return decodeRows(rows)
}

// QueryRow runs a SELECT and returns its first row, or nil when it has none.
func (d *DB) QueryRow(ctx context.Context, query string, vars Vars) (Row, error) {
	rows, err := d.Query(ctx, query, vars)
	if err != nil || len(rows) == 0 {
		return nil, err
	}
	return rows[0], nil
}

// Exec runs a statement and returns the number of affected rows.
func (d *DB) Exec(ctx context.Context, query string, vars Vars) (int64, error) {
	result, err := d.executor.ExecContext(ctx, query, namedArgs(query, vars)...)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

// Int runs a query returning one integer (e.g. a COUNT). No rows yields 0.
func (d *DB) Int(ctx context.Context, query string, vars Vars) (int, error) {
	row, err := d.QueryRow(ctx, query, vars)
	if err != nil || row == nil {
		return 0, err
	}
	for _, value := range row {
		return AsInt(value), nil
	}
	return 0, nil
}

// Strings runs a single-column query and returns the non-NULL string values.
func (d *DB) Strings(ctx context.Context, query string, vars Vars) ([]string, error) {
	rows, err := d.executor.QueryContext(ctx, query, namedArgs(query, vars)...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	values := []string{}
	for rows.Next() {
		var value sql.NullString
		if err := rows.Scan(&value); err != nil {
			return nil, err
		}
		if value.Valid {
			values = append(values, value.String)
		}
	}
	return values, rows.Err()
}

// QueryAs runs a SELECT and decodes each row into T through its json tags.
func QueryAs[T any](ctx context.Context, d *DB, query string, vars Vars) ([]T, error) {
	rows, err := d.Query(ctx, query, vars)
	if err != nil {
		return nil, err
	}
	out := make([]T, 0, len(rows))
	for _, row := range rows {
		var value T
		if err := DecodeRow(row, &value); err != nil {
			return nil, err
		}
		out = append(out, value)
	}
	return out, nil
}

// QueryOneAs is QueryAs for the first row; nil when there is none.
func QueryOneAs[T any](ctx context.Context, d *DB, query string, vars Vars) (*T, error) {
	row, err := d.QueryRow(ctx, query, vars)
	if err != nil || row == nil {
		return nil, err
	}
	var value T
	if err := DecodeRow(row, &value); err != nil {
		return nil, err
	}
	return &value, nil
}

// DecodeRow copies a row into target (a struct pointer) through json tags.
func DecodeRow(row Row, target any) error {
	encoded, err := json.Marshal(row)
	if err != nil {
		return err
	}
	return json.Unmarshal(encoded, target)
}

// parameterPattern finds $name placeholders.
var parameterPattern = regexp.MustCompile(`\$([A-Za-z_][A-Za-z0-9_]*)`)

// namedArgs binds the vars the query references. Unreferenced vars are
// dropped, so callers can build one Vars map for several query shapes.
func namedArgs(query string, vars Vars) []any {
	if len(vars) == 0 {
		return nil
	}
	seen := map[string]bool{}
	args := []any{}
	for _, match := range parameterPattern.FindAllStringSubmatch(query, -1) {
		name := match[1]
		if seen[name] {
			continue
		}
		seen[name] = true
		value, ok := vars[name]
		if !ok {
			continue
		}
		args = append(args, sql.Named(name, bindValue(value)))
	}
	return args
}

// bindValue converts a Go value into something SQLite stores: record ids and
// times become their text forms, collections and structs become JSON text.
func bindValue(value any) any {
	switch typed := value.(type) {
	case nil:
		return nil
	case string, bool, int, int64, int32, float64, float32, []byte:
		return typed
	case time.Time:
		return FormatTime(typed)
	case *time.Time:
		if typed == nil {
			return nil
		}
		return FormatTime(*typed)
	case json.RawMessage:
		return string(typed)
	case json.Number:
		return string(typed)
	}
	reflected := reflect.ValueOf(value)
	switch reflected.Kind() {
	case reflect.Pointer:
		if reflected.IsNil() {
			return nil
		}
		return bindValue(reflected.Elem().Interface())
	case reflect.String:
		return reflected.String()
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		return reflected.Int()
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		return int64(reflected.Uint())
	case reflect.Float32, reflect.Float64:
		return reflected.Float()
	case reflect.Bool:
		return reflected.Bool()
	}
	return encodeJSON(value)
}

// encodeJSON renders value as JSON text; a nil slice becomes "[]" and a nil
// map NULL.
func encodeJSON(value any) any {
	if value == nil {
		return nil
	}
	reflected := reflect.ValueOf(value)
	if reflected.Kind() == reflect.Slice && reflected.IsNil() {
		return "[]"
	}
	if reflected.Kind() == reflect.Map && reflected.IsNil() {
		return nil
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		return nil
	}
	return string(encoded)
}

// decodeRows reads every row, decoding JSON and BOOLEAN columns by their
// declared type.
func decodeRows(rows *sql.Rows) ([]Row, error) {
	columnTypes, err := rows.ColumnTypes()
	if err != nil {
		return nil, err
	}
	names := make([]string, len(columnTypes))
	declared := make([]string, len(columnTypes))
	for index, columnType := range columnTypes {
		names[index] = columnType.Name()
		declared[index] = strings.ToUpper(columnType.DatabaseTypeName())
	}
	out := []Row{}
	for rows.Next() {
		values := make([]any, len(names))
		pointers := make([]any, len(names))
		for index := range values {
			pointers[index] = &values[index]
		}
		if err := rows.Scan(pointers...); err != nil {
			return nil, err
		}
		row := make(Row, len(names))
		for index, name := range names {
			if values[index] == nil {
				continue
			}
			row[name] = decodeColumn(declared[index], values[index])
		}
		out = append(out, row)
	}
	return out, rows.Err()
}

// decodeColumn converts one scanned value according to its declared type.
func decodeColumn(declaredType string, value any) any {
	if raw, isBytes := value.([]byte); isBytes && declaredType != "BLOB" {
		value = string(raw)
	}
	switch declaredType {
	case "JSON":
		text, isText := value.(string)
		if !isText {
			return value
		}
		var decoded any
		decoder := json.NewDecoder(bytes.NewReader([]byte(text)))
		if err := decoder.Decode(&decoded); err != nil {
			return text
		}
		return decoded
	case "BOOLEAN":
		return AsBool(value)
	}
	return value
}

// AsInt reads an integer-ish value (int64, float64, bool, numeric string).
func AsInt(value any) int {
	switch typed := value.(type) {
	case int64:
		return int(typed)
	case int:
		return typed
	case float64:
		return int(typed)
	case bool:
		if typed {
			return 1
		}
		return 0
	case string:
		var parsed int
		fmt.Sscan(typed, &parsed)
		return parsed
	}
	return 0
}

// AsBool reads a boolean-ish value (bool, or a 0/1 integer from an expression).
func AsBool(value any) bool {
	switch typed := value.(type) {
	case bool:
		return typed
	case int64:
		return typed != 0
	case int:
		return typed != 0
	case float64:
		return typed != 0
	}
	return false
}
