package together

import "testing"

func TestNewCodeIsNormalized(t *testing.T) {
	code, err := NewCode()
	if err != nil {
		t.Fatal(err)
	}
	normalized, err := NormalizeCode(code)
	if err != nil || normalized != code {
		t.Fatalf("NormalizeCode(%q) = %q, %v", code, normalized, err)
	}
}

func TestNormalizeCodeAcceptsTypedForms(t *testing.T) {
	for _, input := range []string{"k7qd-9mza", "K7QD 9MZA", "K7QD9MZA"} {
		code, err := NormalizeCode(input)
		if err != nil || code != "K7QD9MZA" {
			t.Errorf("NormalizeCode(%q) = %q, %v", input, code, err)
		}
	}
}

func TestNormalizeCodeRejectsInvalid(t *testing.T) {
	for _, input := range []string{"", "K7QD", "K7QD9MZAX", "O0I1O0I1"} {
		if _, err := NormalizeCode(input); err == nil {
			t.Errorf("NormalizeCode(%q) accepted", input)
		}
	}
}

func TestRoomKeySealsForTheSameCodeOnly(t *testing.T) {
	key, err := deriveRoomKey("K7QD9MZA")
	if err != nil {
		t.Fatal(err)
	}
	sameKey, _ := deriveRoomKey("K7QD9MZA")
	otherKey, _ := deriveRoomKey("K7QD9MZB")
	if key.topic != sameKey.topic || key.topic == otherKey.topic {
		t.Fatalf("topics: %s %s %s", key.topic, sameKey.topic, otherKey.topic)
	}
	sealed, err := key.seal([]byte("hello"))
	if err != nil {
		t.Fatal(err)
	}
	opened, err := sameKey.open(sealed)
	if err != nil || string(opened) != "hello" {
		t.Fatalf("open = %q, %v", opened, err)
	}
	if _, err := otherKey.open(sealed); err == nil {
		t.Fatal("another code opened the message")
	}
}
