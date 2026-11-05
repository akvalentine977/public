# Does WebKitGTK's default script-dialog handler show window.confirm() and block, or return at once?
import gi, sys, time
gi.require_version("Gtk", "3.0"); gi.require_version("WebKit2", "4.1")
from gi.repository import Gtk, WebKit2, GLib
mode = sys.argv[1] if len(sys.argv) > 1 else "default"
win = Gtk.Window(title="wkconfirm"); win.set_default_size(800, 600)
view = WebKit2.WebView(); win.add(view); win.show_all()
if mode == "handled":
    # What an app would do: answer the dialog itself.
    def on_dialog(v, d):
        print("script-dialog signal fired:", d.get_dialog_type(), repr(d.get_message()), flush=True); d.confirm_set_confirmed(False); return True
    view.connect("script-dialog", on_dialog)
t0 = time.time()
html = "<html><body><script>var t0=Date.now(); var r = window.confirm('Discard the unsaved changes to this model?'); document.title = 'confirm=' + r + ' after ' + (Date.now()-t0) + 'ms';</script>x</body></html>"
def changed(v, p):
    if v.get_title():
        print("page says:", v.get_title(), "| wall %.1fs" % (time.time() - t0), flush=True); Gtk.main_quit()
view.connect("notify::title", changed)
view.load_html(html, "file:///")
GLib.timeout_add_seconds(8, lambda: (print("TIMEOUT: confirm() still blocking after 8 s (a dialog is probably showing)", flush=True), Gtk.main_quit()) and False)
Gtk.main()
