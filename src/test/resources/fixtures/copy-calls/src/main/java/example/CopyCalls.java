package example;

import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.function.BiConsumer;

import static org.springframework.beans.BeanUtils.copyProperties;

public class CopyCalls {
    private static final String[] IGNORED = {"items", "tags"};

    public void calls(Source source, Target target, List<Source> sources) {
        BeanUtils.copyProperties(source, target);
        copyProperties(source, target);
        BeanUtils.copyProperties(source, target, "items");
        BeanUtils.copyProperties(source, target, IGNORED);
        BeanUtils.copyProperties(source, target, Editable.class);
        sources.forEach(value -> BeanUtils.copyProperties(value, target));
        BiConsumer<Object, Object> copier = BeanUtils::copyProperties;
        BeanUtils.copyProperties(new MissingSetterSource(), new MissingSetterTarget());
        OtherBeanUtils.copyProperties(source, target);
    }

    public interface Editable { void setName(String name); }
    public static class Source {
        public String getName() { return ""; }
        public String getSourceOnly() { return ""; }
    }
    public static class Target implements Editable {
        public void setName(String name) { }
        public String getTargetOnly() { return ""; }
        public void setTargetOnly(String value) { }
    }
    public static class MissingSetterSource { public String getExternalId() { return "source"; } }
    public static class MissingSetterTarget { public String getExternalId() { return "target"; } }
}
