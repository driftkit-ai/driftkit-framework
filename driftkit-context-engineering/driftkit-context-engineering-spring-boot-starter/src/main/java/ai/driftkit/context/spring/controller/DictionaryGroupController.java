package ai.driftkit.context.spring.controller;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.DictionaryGroup;
import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.context.core.service.DictionaryGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping(path = "/data/v1.0/admin/dictionary/group")
public class DictionaryGroupController {

    @Autowired
    private DictionaryGroupService dictionaryGroupService;

    @GetMapping("/")
    public @ResponseBody RestResponse<List<DictionaryGroup>> getGroups() {
        return new RestResponse<>(true, dictionaryGroupService.findAll());
    }

    @GetMapping("/{id}")
    public @ResponseBody RestResponse<DictionaryGroup> getGroup(@PathVariable String id) {
        Optional<DictionaryGroup> group = dictionaryGroupService.findById(id);
        return new RestResponse<>(group.isPresent(), group.orElse(null));
    }

    @PostMapping("/")
    public @ResponseBody RestResponse<DictionaryGroup> saveGroup(@RequestBody DictionaryGroup group) {
        fixGroup(group);
        DictionaryGroup savedGroup = dictionaryGroupService.save(group);
        return new RestResponse<>(true, savedGroup);
    }

    @DeleteMapping("/{id}")
    public @ResponseBody RestResponse<Boolean> deleteGroup(@PathVariable String id) {
        dictionaryGroupService.deleteById(id);
        return new RestResponse<>(true, true);
    }

    private void fixGroup(DictionaryGroup group) {
        if (group.getId() != null) {
            group.setId(group.getId().toLowerCase(Locale.ROOT));
        }
        if (group.getLanguage() == null) {
            group.setLanguage(Language.GENERAL);
        }
    }
}