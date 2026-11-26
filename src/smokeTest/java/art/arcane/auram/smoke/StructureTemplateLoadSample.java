package art.arcane.auram.smoke;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("auram.StructureTemplateLoad")
@Label("Structure template load")
@StackTrace(false)
public final class StructureTemplateLoadSample extends Event {
    public String resourceId;
    public int manager;
    public boolean found;
}
