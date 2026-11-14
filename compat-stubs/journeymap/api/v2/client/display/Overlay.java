package journeymap.api.v2.client.display;

public abstract class Overlay extends Displayable {
    Overlay(String modId) { super(modId); }
    public Overlay setTitle(String title) { return this; }
    public Overlay setLabel(String label) { return this; }
}
