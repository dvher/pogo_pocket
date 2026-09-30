package pogocore

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// startServer builds and runs Pogo Pad from ../../server (or ../../pogo_pad)
// and returns its port and a token.
func startServer(t *testing.T) (port, token string) {
	t.Helper()
	var serverDir string
	for _, dir := range []string{"../../server", "../../pogo_pad"} {
		if _, err := os.Stat(filepath.Join(dir, "cmd", "pogo-pad")); err == nil {
			serverDir, _ = filepath.Abs(dir)
			break
		}
	}
	if serverDir == "" {
		t.Skip("Pogo Pad repo not found next to this one")
	}
	dir := t.TempDir()
	bin := filepath.Join(dir, "pogo-pad")
	build := exec.Command("go", "build", "-o", bin, "./cmd/pogo-pad")
	build.Dir = serverDir
	if out, err := build.CombinedOutput(); err != nil {
		t.Fatalf("build server: %v\n%s", err, out)
	}
	db := filepath.Join(dir, "server.db")
	out, err := exec.Command(bin, "token", "create", "--db", db, "--name", "test").Output()
	if err != nil {
		t.Fatal(err)
	}
	token = strings.TrimSpace(string(out))
	l, _ := net.Listen("tcp", "127.0.0.1:0")
	port = fmt.Sprint(l.Addr().(*net.TCPAddr).Port)
	l.Close()
	cmd := exec.Command(bin, "serve", "--addr", "127.0.0.1:"+port, "--db", db)
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { cmd.Process.Kill(); cmd.Wait() })
	for range 50 {
		if resp, err := http.Get("http://127.0.0.1:" + port + "/api/v1/health"); err == nil {
			resp.Body.Close()
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	t.Fatal("server did not start")
	return
}

type recorder struct{ notes, statuses int }

func (r *recorder) NotesChanged()        { r.notes++ }
func (r *recorder) StatusChanged(string) { r.statuses++ }

func open(t *testing.T) *Core {
	t.Helper()
	c, err := Open(t.TempDir(), NewKey())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	return c
}

func connect(t *testing.T, c *Core, port, token string) {
	t.Helper()
	js := fmt.Sprintf(`{"scheme":"http","host":"127.0.0.1","port":%q,"token":%q,"enabled":true,"intervalSec":30}`, port, token)
	if _, err := c.SaveSettingsJSON(js); err != nil {
		t.Fatal(err)
	}
}

func view(t *testing.T, c *Core, key string) WidgetView {
	t.Helper()
	js, err := c.WidgetViewJSON(key)
	if err != nil {
		t.Fatal(err)
	}
	var v WidgetView
	if err := json.Unmarshal([]byte(js), &v); err != nil {
		t.Fatal(err)
	}
	return v
}

func mustSync(t *testing.T, c *Core) {
	t.Helper()
	if err := c.SyncNow(); err != nil {
		t.Fatalf("sync: %v", err)
	}
}

func TestOpenWrongKey(t *testing.T) {
	dir := t.TempDir()
	c, err := Open(dir, NewKey())
	if err != nil {
		t.Fatal(err)
	}
	c.Create("secret")
	c.Close()
	c, _ = Open(dir, NewKey())
	defer c.Close()
	if _, err := c.NotesJSON(); err == nil {
		t.Fatal("read notes with the wrong key")
	}
	if _, err := Open(dir, "not base64!"); err == nil {
		t.Fatal("accepted a bad key")
	}
}

func TestWidgetLocal(t *testing.T) {
	c := open(t)
	rec := &recorder{}
	c.SetListener(rec)

	if v := view(t, c, "7"); v.State != "empty" || v.Count != 0 {
		t.Fatalf("new db: %+v", v)
	}
	if !c.EnsureWelcome() || c.EnsureWelcome() {
		t.Fatal("welcome note should be created exactly once")
	}
	a, _ := c.NotesJSON()
	var notes []Note
	json.Unmarshal([]byte(a), &notes)
	welcome := notes[0].ID
	b, _ := c.Create("# Second\n- [ ] task")
	d, _ := c.Create("")
	if rec.notes != 3 {
		t.Fatalf("listener saw %d changes", rec.notes)
	}

	// An unset widget shows the first note; the corner walks through all and wraps.
	var seen []string
	for range 4 {
		v := view(t, c, "7")
		if v.State != "note" || v.Count != 3 {
			t.Fatalf("%+v", v)
		}
		seen = append(seen, v.Note.ID)
		c.WidgetNext("7")
	}
	if strings.Join(seen, ",") != strings.Join([]string{welcome, b, d, welcome}, ",") {
		t.Fatalf("order %v", seen)
	}

	// Widgets are independent; a picked note sticks.
	if err := c.WidgetSetNote("8", b); err != nil {
		t.Fatal(err)
	}
	v := view(t, c, "8")
	if v.Note.ID != b || v.Lines[0].Text != "Second" || v.Lines[1].Kind != "task" || v.Index != 1 {
		t.Fatalf("%+v", v)
	}
	if view(t, c, "7").Note.ID != b {
		t.Fatal("widget 7 moved")
	}

	// Toggling a task from the widget edits the Markdown.
	if err := c.ToggleTask(b, v.Lines[1].Line); err != nil {
		t.Fatal(err)
	}
	if v := view(t, c, "8"); !v.Lines[1].Checked {
		t.Fatal("task not toggled")
	}

	// Color comes through with the palette's hex values.
	c.SetColor(b, "pink")
	if v := view(t, c, "8"); v.BG != "#f8bbd0" || v.Note.Color != "pink" {
		t.Fatalf("color %+v", v)
	}
	if c.SetColor(b, "neon") == nil {
		t.Fatal("unknown color accepted")
	}

	// Deleting the shown note moves the widget to the note that took its place.
	c.Delete(b)
	if v := view(t, c, "8"); v.Note.ID != d || v.Count != 2 {
		t.Fatalf("after delete %+v", v)
	}
	c.Delete(d)
	if v := view(t, c, "8"); v.Note.ID != welcome {
		t.Fatalf("after deleting the last note %+v", v)
	}
	c.Delete(welcome)
	if v := view(t, c, "8"); v.State != "empty" || v.BG == "" {
		t.Fatalf("no notes %+v", v)
	}
	if err := c.WidgetNext("8"); err != nil {
		t.Fatal(err)
	}
	c.WidgetRemove("8")
}

func TestSyncWithDesktopProtocol(t *testing.T) {
	port, token := startServer(t)
	phone, other := open(t), open(t)
	connect(t, phone, port, token)
	connect(t, other, port, token)

	// Nothing on the server: the widget shows the empty state.
	mustSync(t, phone)
	if v := view(t, phone, "1"); v.State != "empty" {
		t.Fatalf("%+v", v)
	}

	// A note from another device shows up on the widget after a sync.
	id, _ := other.Create("# Shopping\n- [ ] milk")
	mustSync(t, other)
	rec := &recorder{}
	phone.SetListener(rec)
	mustSync(t, phone)
	if rec.notes == 0 || rec.statuses == 0 {
		t.Fatalf("listener not called: %+v", rec)
	}
	v := view(t, phone, "1")
	if v.State != "note" || v.Note.ID != id {
		t.Fatalf("%+v", v)
	}

	// Ticking the task on the widget reaches the other device.
	phone.ToggleTask(id, v.Lines[1].Line)
	mustSync(t, phone)
	mustSync(t, other)
	js, _ := other.NoteJSON(id)
	if !strings.Contains(js, `- [x] milk`) {
		t.Fatalf("other device has %s", js)
	}

	// End-to-end encryption turned on elsewhere locks the phone until it
	// has the passphrase; with no notes yet, the widget says so.
	fresh := open(t)
	connect(t, fresh, port, token)
	if err := other.EnableE2E("correct horse"); err != nil {
		t.Fatal(err)
	}
	if err := fresh.SyncNow(); err == nil {
		t.Fatal("fresh device synced without the passphrase")
	}
	if v := view(t, fresh, "1"); v.State != "locked" {
		t.Fatalf("%+v", v)
	}
	if err := fresh.EnableE2E("correct horse"); err != nil {
		t.Fatal(err)
	}
	if v := view(t, fresh, "1"); v.State != "note" || v.Note.Title != "Shopping" {
		t.Fatalf("%+v", v)
	}
	var st struct{ E2EEnabled bool }
	json.Unmarshal([]byte(fresh.StatusJSON()), &st)
	if !st.E2EEnabled {
		t.Fatal("status does not report E2E")
	}

	// Deleting everything empties the widget everywhere.
	other.Delete(id)
	mustSync(t, other)
	mustSync(t, fresh)
	if v := view(t, fresh, "1"); v.State != "empty" {
		t.Fatalf("%+v", v)
	}

	// Connection check.
	if msg, err := phone.TestConnectionJSON(phone.SettingsJSON()); err != nil || !strings.Contains(msg, "Token OK") {
		t.Fatalf("%q %v", msg, err)
	}
}
