package ai.driftkit.context.spring.controller;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.DictionaryItem;
import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.context.core.service.DictionaryItemService;
import ai.driftkit.context.core.service.DictionaryGroupService;
import ai.driftkit.common.domain.DictionaryGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/admin/dictionary/")
public class DictionaryController {

    @Autowired
    private DictionaryItemService dictionaryItemService;

    @Autowired
    private DictionaryGroupService dictionaryGroupService;

    @GetMapping("/")
    public @ResponseBody RestResponse<List<DictionaryItem>> getDictionaryItems(
            @RequestParam(required = false) Language language,
            @RequestParam(required = false) String groupId
    ) {
        List<DictionaryItem> items;

        if (StringUtils.isNotBlank(groupId)) {
            items = dictionaryItemService.findByGroupId(groupId);
        } else if (language != null) {
            items = dictionaryItemService.findByLanguage(language);
        } else {
            items = dictionaryItemService.findAll();
        }

        return new RestResponse<>(
                true,
                items
        );
    }

    @GetMapping("/{id}")
    public @ResponseBody RestResponse<DictionaryItem> getDictionaryItem(@PathVariable String id) {
        Optional<DictionaryItem> item = dictionaryItemService.findById(id);

        return new RestResponse<>(
                item.isPresent(),
                item.orElse(null)
        );
    }

    @PostMapping("/text")
    public @ResponseBody RestResponse<DictionaryItem> updateDictionaryIdAsText(
            @RequestBody DictionaryTextData request
    ) {
        List<String> markers = splitText(request.getMarkers());
        List<String> samples = splitText(request.getExamples());

        fixRequest(request);

        Optional<DictionaryGroup> group = dictionaryGroupService.findById(request.getGroupId());

        DictionaryItem item = dictionaryItemService.save(new DictionaryItem(
                request.id,
                request.index,
                request.groupId,
                request.name,
                group.map(DictionaryGroup::getLanguage).orElse(null),
                markers,
                samples,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        ));

        return new RestResponse<>(
                true,
                item
        );
    }

    @PostMapping("/texts")
    public @ResponseBody RestResponse<List<DictionaryItem>> updateDictionaryIdAsText(
            @RequestBody List<DictionaryTextData> request
    ) {
        List<DictionaryItem> result = new ArrayList<>();

        for (DictionaryTextData data : request) {
            List<String> markers = splitText(data.getMarkers());
            List<String> samples = splitText(data.getExamples());

            fixRequest(data);

            Optional<DictionaryGroup> group = dictionaryGroupService.findById(data.getGroupId());
            Language language = group.map(DictionaryGroup::getLanguage).orElse(null);

            DictionaryItem item = dictionaryItemService.save(new DictionaryItem(
                    data.id,
                    data.index,
                    data.groupId,
                    data.name,
                    language,
                    markers,
                    samples,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            ));
            result.add(item);
        }

        return new RestResponse<>(
                true,
                result
        );
    }

    @PostMapping("/items")
    public @ResponseBody RestResponse<List<DictionaryItem>> updateDictionaryIdAsItems(
            @RequestBody List<DictionaryItem> items
    ) {
        Map<String, List<DictionaryItem>> group2items = items.stream().collect(Collectors.groupingBy(DictionaryItem::getGroupId));

        for (Entry<String, List<DictionaryItem>> group2list : group2items.entrySet()) {
            if (StringUtils.isNotBlank(group2list.getKey())) {
                Optional<DictionaryGroup> byId = dictionaryGroupService.findById(group2list.getKey());

                Language language = byId.map(DictionaryGroup::getLanguage).orElse(null);

                for (DictionaryItem item : group2list.getValue()) {
                    item.setLanguage(language);
                }
            }
        }

        List<DictionaryItem> item = dictionaryItemService.saveAll(items);

        return new RestResponse<>(true, item);
    }

    @PostMapping("/")
    public @ResponseBody RestResponse<DictionaryItem> updateDictionaryIdAsData(
            @RequestBody DictionaryData request
    ) {
        List<String> markers = request.getMarkers();
        List<String> samples = request.getExamples();

        if (request.name == null) {
            request.name = request.id;
        }

        String groupId = request.getGroupId();
        Language language = null;

        if (StringUtils.isNotBlank(groupId)) {
            Optional<DictionaryGroup> byId = dictionaryGroupService.findById(groupId);

            language = byId.map(DictionaryGroup::getLanguage).orElse(null);
        }

        DictionaryItem item = dictionaryItemService.save(new DictionaryItem(
                request.id,
                request.index,
                request.groupId,
                request.name,
                language,
                markers,
                samples,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        ));

        return new RestResponse<>(
                true,
                item
        );
    }

    private static List<String> splitText(String markerText) {
        if (StringUtils.isBlank(markerText)) {
            return null;
        }

        String lineBreak;

        if (markerText.contains(",")) {
            lineBreak = ",";
        } else if (markerText.contains("\n")) {
            lineBreak = "\n";
        } else {
            return List.of(markerText);
        }

        List<String> result = new ArrayList<>();

        String[] split = markerText.split(lineBreak);

        for (String s : split) {
            result.add(s.trim());
        }

        return result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DictionaryTextData {
        private String id;
        private int index;
        private String name;
        private String groupId;
        private String markers;
        private String examples;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DictionaryData {
        private String id;
        private int index;
        private String name;
        private String groupId;
        private List<String> markers;
        private List<String> examples;
    }

    private static void fixRequest(DictionaryTextData data) {
        if (data.name == null) {
            data.name = data.id;
        }

        if (data.id != null) {
            data.id = data.id.toLowerCase(Locale.ROOT);
        }
    }
}