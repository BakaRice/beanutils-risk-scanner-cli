package fixture.facade;

import fixture.api.SourceDto;
import org.springframework.beans.BeanUtils;

public class CrossModuleConverter {
    public void copy(SourceDto source, TargetDto target) {
        BeanUtils.copyProperties(source, target);
    }
}
