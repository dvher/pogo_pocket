package pogocore

import (
	"encoding/json"

	"github.com/dvher/pogo/pkg/pogosync"
)

// SyncNow syncs with Pogo Pad now. It returns nil when sync is turned off.
func (c *Core) SyncNow() error {
	if err := c.engine.SyncNow(); err != pogosync.ErrDisabled {
		return err
	}
	return nil
}

// SyncEnabled reports whether sync is set up and turned on.
func (c *Core) SyncEnabled() bool {
	s := c.engine.Settings()
	return s.Enabled && s.Host != ""
}

// StatusJSON returns the sync status (pogosync.Status) as JSON.
func (c *Core) StatusJSON() string { return toJSON(c.engine.Status()) }

// SettingsJSON returns the sync settings (pogosync.Settings) as JSON.
func (c *Core) SettingsJSON() string { return toJSON(c.engine.Settings()) }

// SaveSettingsJSON stores settings given as JSON and returns them normalized.
func (c *Core) SaveSettingsJSON(settingsJSON string) (string, error) {
	var in pogosync.Settings
	if err := json.Unmarshal([]byte(settingsJSON), &in); err != nil {
		return "", err
	}
	out, err := c.engine.SaveSettings(in)
	if err != nil {
		return "", err
	}
	return toJSON(out), nil
}

// TestConnectionJSON checks unsaved settings against the server and returns
// a message for the user.
func (c *Core) TestConnectionJSON(settingsJSON string) (string, error) {
	var in pogosync.Settings
	if err := json.Unmarshal([]byte(settingsJSON), &in); err != nil {
		return "", err
	}
	return c.engine.TestConnection(in)
}

// EnableE2E turns on end-to-end encryption, or unlocks it on this device if
// the server already uses it.
func (c *Core) EnableE2E(passphrase string) error { return c.engine.EnableE2E(passphrase) }

// DisableE2E turns off end-to-end encryption for every device on the server.
func (c *Core) DisableE2E() error { return c.engine.DisableE2E() }
