package art.arcane.auram.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public final class ProjectEMapCapture {
    private ProjectEMapCapture() {}

    public static void write(long elapsedNanos) {
        try {
            Class<?> handler = Class.forName("moze_intel.projecte.emc.EMCMappingHandler");
            Class<?> itemInfo = Class.forName("moze_intel.projecte.api.ItemInfo");
            var getValue = handler.getMethod("getStoredEmcValue", itemInfo);
            var entries = new ArrayList<String>();
            for (Object item : (Set<?>) handler.getMethod("getMappedItems").invoke(null)) {
                entries.add(item + "\t" + getValue.invoke(null, item));
            }
            Collections.sort(entries);
            int index = (int) handler.getMethod("getLoadIndex").invoke(null);
            Files.write(Path.of("projecte-emc-" + index + ".tsv"), entries);
            System.out.println("AURAM_PROJECTE_MAP elapsed_ns=" + elapsedNanos + " rows=" + entries.size());
        } catch (ReflectiveOperationException | IOException failure) {
            throw new IllegalStateException("Could not capture ProjectE mapping parity", failure);
        }
    }
}
