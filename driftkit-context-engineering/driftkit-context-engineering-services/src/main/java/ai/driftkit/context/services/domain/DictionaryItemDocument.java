package ai.driftkit.context.services.domain;

import ai.driftkit.common.domain.DictionaryItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "dictionary_items")
public class DictionaryItemDocument extends DictionaryItem {
    
    @Id
    @Override
    public String getId() {
        return super.getId();
    }

    @Override
    public String getGroupId() {
        return super.getGroupId();
    }
}