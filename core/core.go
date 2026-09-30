// Package pogocore is the shared engine of Pogo Pocket: the encrypted note
// store, Pogo Pad sync and the widget's view model. Android and iOS apps use
// it through gomobile bindings, so its API sticks to strings, numbers, bools,
// errors and JSON.
package pogocore

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/google/uuid"

	"github.com/dvher/pogo/pkg/palette"
	"github.com/dvher/pogo/pkg/pogosync"
	"github.com/dvher/pogo/pkg/secure"
	"github.com/dvher/pogo/pkg/store"
)

const (
	keyWelcomed = "welcomed"
	maxContent  = 1 << 20
)

// Listener receives change notifications. Callbacks may arrive on any thread.
type Listener interface {
	// NotesChanged is called after notes were edited locally or by a sync.
	NotesChanged()
	// StatusChanged is called with the sync status as JSON.
	StatusChanged(statusJSON string)
}

// Core is an open Pogo Pocket database.
type Core struct {
	store  *store.Store
	engine *pogosync.Engine

	mu       sync.Mutex
	listener Listener
}

// Open opens (or creates) the database in dataDir. keyB64 is the 32-byte
// at-rest key in standard base64; the host keeps it in the platform keystore.
func Open(dataDir, keyB64 string) (*Core, error) {
	key, err := base64.StdEncoding.DecodeString(keyB64)
	if err != nil {
		return nil, errors.New("key is not base64")
	}
	cipher, err := secure.NewCipher(key)
	if err != nil {
		return nil, err
	}
	st, err := store.Open(dataDir, cipher)
	if err != nil {
		return nil, err
	}
	c := &Core{store: st, engine: pogosync.New(st, pogosync.DeviceID(st))}
	c.engine.OnStatus = func(s pogosync.Status) {
		if l := c.getListener(); l != nil {
			l.StatusChanged(toJSON(s))
		}
	}
	c.engine.OnApplied = func([]pogosync.Applied) { c.notesChanged() }
	return c, nil
}

// NewKey returns a fresh random at-rest key in standard base64.
func NewKey() string { return base64.StdEncoding.EncodeToString(secure.NewKey()) }

// Close closes the database.
func (c *Core) Close() error { return c.store.Close() }

// SetListener registers l for change notifications (nil to remove).
func (c *Core) SetListener(l Listener) {
	c.mu.Lock()
	c.listener = l
	c.mu.Unlock()
}

func (c *Core) getListener() Listener {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.listener
}

func (c *Core) notesChanged() {
	if l := c.getListener(); l != nil {
		l.NotesChanged()
	}
}

// Note is a note as the apps see it.
type Note struct {
	ID        string `json:"id"`
	Title     string `json:"title"`
	Content   string `json:"content"`
	Color     string `json:"color"`
	CreatedAt int64  `json:"createdAt"`
	UpdatedAt int64  `json:"updatedAt"`
}

func fromStore(n store.Note) Note {
	return Note{ID: n.ID, Title: TitleOf(n.Content), Content: n.Content, Color: n.Color, CreatedAt: n.CreatedAt, UpdatedAt: n.UpdatedAt}
}

// NotesJSON returns all notes, oldest first, as a JSON array of Note.
func (c *Core) NotesJSON() (string, error) {
	notes, err := c.store.List()
	if err != nil {
		return "", err
	}
	out := make([]Note, len(notes))
	for i, n := range notes {
		out[i] = fromStore(n)
	}
	return toJSON(out), nil
}

// NoteJSON returns one note as JSON. Deleted notes are reported as not found.
func (c *Core) NoteJSON(id string) (string, error) {
	n, err := c.store.Get(id)
	if err == nil && n.Deleted {
		err = store.ErrNotFound
	}
	if err != nil {
		return "", err
	}
	return toJSON(fromStore(n)), nil
}

const defaultContent = "# New note\n\n- [ ] something to do\n"

// Create adds a note and returns its id. Empty content gets a starter note.
func (c *Core) Create(content string) (string, error) {
	if content == "" {
		content = defaultContent
	}
	if len(content) > maxContent {
		return "", errTooLong
	}
	now := time.Now().UnixMilli()
	n := store.Note{
		ID: uuid.NewString(), Content: content, Color: palette.Default,
		CreatedAt: now, UpdatedAt: now, DeviceID: c.engine.DeviceID(), W: 260, H: 260,
	}
	if err := c.store.Insert(n); err != nil {
		return "", err
	}
	c.edited()
	return n.ID, nil
}

var errTooLong = errors.New("note is too long (max 1 MiB)")

// SetContent replaces a note's Markdown.
func (c *Core) SetContent(id, content string) error {
	if len(content) > maxContent {
		return errTooLong
	}
	if _, err := c.store.Edit(id, c.engine.DeviceID(), &content, nil, false); err != nil {
		return err
	}
	c.edited()
	return nil
}

// SetColor changes a note's color (see PaletteJSON).
func (c *Core) SetColor(id, color string) error {
	if !palette.Valid(color) {
		return fmt.Errorf("unknown color %q", color)
	}
	if _, err := c.store.Edit(id, c.engine.DeviceID(), nil, &color, false); err != nil {
		return err
	}
	c.edited()
	return nil
}

// Delete removes a note on every device.
func (c *Core) Delete(id string) error {
	if _, err := c.store.Edit(id, c.engine.DeviceID(), nil, nil, true); err != nil {
		return err
	}
	c.edited()
	return nil
}

// ToggleTask flips the checkbox on 0-based source line of a note. Lines that
// are not tasks are left alone.
func (c *Core) ToggleTask(id string, line int) error {
	n, err := c.store.Get(id)
	if err != nil {
		return err
	}
	toggled := ToggleTask(n.Content, line)
	if toggled == n.Content {
		return nil
	}
	return c.SetContent(id, toggled)
}

func (c *Core) edited() {
	c.notesChanged()
	c.engine.Nudge()
}

// EnsureWelcome creates a welcome note on the very first run and reports
// whether it did.
func (c *Core) EnsureWelcome() bool {
	if c.store.Setting(keyWelcomed, "") != "" {
		return false
	}
	c.store.SetSetting(keyWelcomed, "1")
	if !c.store.Empty() {
		return false
	}
	_, err := c.Create(welcomeNote)
	return err == nil
}

const welcomeNote = `# Welcome to Pogo Pocket 🟨

- [x] Add the Pogo widget to your home screen
- [ ] Tap a task to cross it off
- [ ] Tap the folded corner for the next note
- [ ] Tap the text to edit

Open the app to set up sync with your **Pogo Pad**.
`

// PaletteJSON returns the note colors as [{name, bg, ink, accent}] with
// "#rrggbb" values.
func PaletteJSON() string {
	type color struct {
		Name   string `json:"name"`
		BG     string `json:"bg"`
		Ink    string `json:"ink"`
		Accent string `json:"accent"`
	}
	out := make([]color, len(palette.Colors))
	for i, p := range palette.Colors {
		out[i] = color{p.Name, hexRGB(p.BG), hexRGB(p.Ink), hexRGB(p.Accent)}
	}
	return toJSON(out)
}

func hexRGB(c [3]uint8) string { return fmt.Sprintf("#%02x%02x%02x", c[0], c[1], c[2]) }

func toJSON(v any) string {
	b, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return string(b)
}
