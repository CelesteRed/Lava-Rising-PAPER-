import java.io.InputStream;
import java.util.jar.JarFile;
import org.bukkit.plugin.PluginDescriptionFile;

public class VerifyPluginDescription {
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0]); InputStream in = jar.getInputStream(jar.getEntry("plugin.yml"))) {
            PluginDescriptionFile desc = new PluginDescriptionFile(in);
            System.out.println(desc.getName() + " " + desc.getVersion() + " api=" + desc.getAPIVersion() + " main=" + desc.getMain());
        }
    }
}
