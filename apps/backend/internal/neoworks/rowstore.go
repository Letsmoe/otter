package neoworks

import (
	"encoding/json"
	"fmt"

	libneoworks "github.com/neoworks-dev/libneoworks-go"
)

// rowStore is what the mirrors need of libneoworks' client; tests replace it.
type rowStore interface {
	createRow(table string, content map[string]any) (string, error)
	updateRow(rowID string, content map[string]any) error
	deleteRow(rowID string) error
	// rows returns the live rows of table by id, and the ids of its deleted rows.
	rows(table string) (map[string]map[string]any, map[string]bool, error)
	// hasRoot reports whether the store holds the collection's root: it was
	// set up and not wiped, so a mirrored row missing from it was deleted.
	hasRoot() (bool, error)
}

// clientRows is rowStore over the libneoworks client, in the @letsmoe/otter collection.
type clientRows struct {
	client *libneoworks.Client
}

func (store clientRows) createRow(table string, content map[string]any) (string, error) {
	return store.client.CreateRow(libneoworks.NewRow{Collection: collection, Table: table, Content: content})
}

func (store clientRows) updateRow(rowID string, content map[string]any) error {
	return store.client.UpdateNode(libneoworks.NodeUpdate{NodeID: rowID, Changes: content})
}

func (store clientRows) deleteRow(rowID string) error {
	_, err := store.client.DeleteNode(rowID)
	return err
}

func (store clientRows) rows(table string) (map[string]map[string]any, map[string]bool, error) {
	nodes, err := store.client.ListNodes("", collection)
	if err != nil {
		return nil, nil, err
	}
	live := map[string]map[string]any{}
	deleted := map[string]bool{}
	for _, node := range nodes {
		if node.Table == nil || *node.Table != table {
			continue
		}
		if node.Deleted {
			deleted[node.ID] = true
			continue
		}
		content, err := store.readContent(node.ID)
		if err != nil {
			return nil, nil, err
		}
		live[node.ID] = content
	}
	return live, deleted, nil
}

func (store clientRows) hasRoot() (bool, error) {
	nodes, err := store.client.ListNodes("", collection)
	if err != nil {
		return false, err
	}
	for _, node := range nodes {
		if node.Kind == libneoworks.NodeKindRoot && !node.Deleted {
			return true, nil
		}
	}
	return false, nil
}

func (store clientRows) readContent(rowID string) (map[string]any, error) {
	read, err := store.client.ReadRow(rowID)
	if err != nil {
		return nil, fmt.Errorf("read row %s: %w", rowID, err)
	}
	var content map[string]any
	if err := json.Unmarshal(read.Content, &content); err != nil {
		return nil, fmt.Errorf("read row %s: %w", rowID, err)
	}
	return content, nil
}
