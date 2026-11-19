import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.rewriter.DexRewriter;
import com.android.tools.smali.dexlib2.rewriter.Rewriter;
import com.android.tools.smali.dexlib2.rewriter.RewriterModule;
import com.android.tools.smali.dexlib2.rewriter.Rewriters;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/** 用 DEX 类型重写器迁移配置包，先验证实际模块的公开接口。 */
public final class ConfigJarMigrator {
    private static final String OLD = "Lcom/fongmi/quickjs/";
    private static final String OWN = "Lcom/kukuqi/quickjs/";
    private static String migrate(String type) { return type.replace(OLD, OWN); }
    private static String descriptor(Class<?> type) { return type.descriptorString(); }
    private static Class<?> resolve(String type) throws Exception {
        return Class.forName(type.substring(1, type.length() - 1).replace('/', '.'), false, ConfigJarMigrator.class.getClassLoader());
    }
    private static String signature(MethodReference method) {
        return "(" + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }
    private static String signature(Class<?>[] parameters, Class<?> result) {
        StringBuilder value = new StringBuilder("(");
        for (Class<?> parameter : parameters) value.append(descriptor(parameter));
        return value.append(")").append(descriptor(result)).toString();
    }
    private static void verify(DexBackedDexFile dex) throws Exception {
        Set<String> checked = new TreeSet<>();
        for (var reference : dex.getTypeReferences()) {
            String type = reference.getType().replaceFirst("^\\[+", "");
            if (type.startsWith(OLD) || type.startsWith(OWN)) resolve(migrate(type));
        }
        for (var reference : dex.getStringReferences()) {
            String value = reference.getString();
            if (value.contains("com.fongmi.quickjs")) throw new IllegalArgumentException("存在反射包名，必须按源码处理：" + value);
        }
        for (var reference : dex.getFieldSection()) {
            if (reference.getDefiningClass().startsWith(OLD) || reference.getDefiningClass().startsWith(OWN)) {
                var field = resolve(migrate(reference.getDefiningClass())).getField(reference.getName());
                if (!descriptor(field.getType()).equals(migrate(reference.getType()))) throw new NoSuchFieldException(reference.toString());
            }
        }
        for (var reference : dex.getMethodSection()) {
            if (!reference.getDefiningClass().startsWith(OLD) && !reference.getDefiningClass().startsWith(OWN)) continue;
            String expected = migrate(signature(reference));
            Class<?> owner = resolve(migrate(reference.getDefiningClass()));
            boolean exists = false;
            if (reference.getName().equals("<init>")) {
                for (Constructor<?> constructor : owner.getConstructors()) exists |= signature(constructor.getParameterTypes(), void.class).equals(expected);
            } else {
                for (Method method : owner.getMethods()) exists |= method.getName().equals(reference.getName()) && signature(method.getParameterTypes(), method.getReturnType()).equals(expected);
            }
            if (!exists) throw new NoSuchMethodException(reference.toString());
            checked.add(owner.getName() + "->" + reference.getName() + expected);
        }
        for (String method : checked) System.out.println("已验证 " + method);
    }
    public static void main(String[] args) throws Exception {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(args[0], Opcodes.getDefault());
        verify(dex);
        DexRewriter rewriter = new DexRewriter(new RewriterModule() {
            @Override public Rewriter<String> getTypeRewriter(Rewriters rewriters) { return ConfigJarMigrator::migrate; }
        });
        var rewritten = rewriter.getDexFileRewriter().rewrite(dex);
        if (rewritten.getClasses().size() != dex.getClasses().size()) throw new IllegalStateException("迁移丢失 DEX 类");
        DexFileFactory.writeDexFile(args[1], rewritten);
        System.out.println("保留 DEX 类 " + dex.getClasses().size());
    }
}
