package fixture.facade;

import fixture.api.GenericBase;
import java.util.List;

public class TargetDto extends GenericBase<Long> {
    public List<Long> getItems() { return null; }
    public void setItems(List<Long> items) { }
    public String getTargetOnly() { return null; }
    public void setTargetOnly(String value) { }
    public String getReadOnly() { return null; }
}
