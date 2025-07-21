package ai.driftkit.vector.spring.service;

import ai.driftkit.vector.spring.domain.ParsedContent;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import ai.driftkit.vector.spring.repository.ParsedContentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class ParserService {

    @Autowired
    private ParsedContentRepository parsedContentRepository;

    @Autowired
    private UnifiedParser parser;

    public ParsedContent parse(ParserInput input, boolean save) throws IOException {
        ParsedContent parse = parser.parse(input);

        if (save) {
            save(parse);
        }

        return parse;
    }

    public void save(ParsedContent parse) {
        parsedContentRepository.save(parse);
    }
}
