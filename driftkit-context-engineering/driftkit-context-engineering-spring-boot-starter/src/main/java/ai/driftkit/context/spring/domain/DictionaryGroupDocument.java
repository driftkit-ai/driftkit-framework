package ai.driftkit.context.spring.domain;

import ai.driftkit.common.domain.DictionaryGroup;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "dictionary_groups")
public class DictionaryGroupDocument extends DictionaryGroup {
    
    @Id
    @Override
    public String getId() {
        return super.getId();
    }
}