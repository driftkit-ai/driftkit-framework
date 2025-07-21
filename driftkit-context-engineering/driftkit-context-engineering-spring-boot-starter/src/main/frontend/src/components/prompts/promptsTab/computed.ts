import { computed, ComputedRef } from 'vue';
import { PromptsState, TestRunState } from './types';
import { highlightVariables, formatJSON, isJSON } from '../../../utils/formatting';

export function usePromptsComputed(state: PromptsState) {
  // Filter prompts based on current folder and search query
  const filteredPrompts = computed(() => {
    console.log('📋 Computing filteredPrompts with prompts:', state.prompts.value);

    if (!state.prompts.value || state.prompts.value.length === 0) {
      console.log('❗ No prompts available - empty array or undefined');
      return [];
    }

    console.log('📊 Working with prompts array, length:', state.prompts.value.length);

    // Helper function to check if a prompt should be included based on the folder structure
    const shouldIncludePromptInFolder = (promptMethod: string) => {
      if (!promptMethod) return false;

      if (state.currentFolder.value) {
        // Current folder specified - check if this prompt belongs directly to this folder
        // It should start with the current folder path and have exactly one more level
        const prefix = state.currentFolder.value + '/';
        if (!promptMethod.startsWith(prefix)) return false;

        // Get the relative path after the current folder
        const relativePath = promptMethod.substring(prefix.length);

        // Include if:
        // 1. It doesn't have any more subfolders (direct child of current folder)
        // 2. Or it's a subfolder of the current folder (used to create folder entries)
        return !relativePath.includes('/') || relativePath.split('/').length === 2;
      } else {
        // Root folder - include top-level methods and first level folders
        if (promptMethod.includes('/')) {
          // For folder entries, only include first-level folders
          return promptMethod.split('/').length === 2;
        }
        return true; // Include all top-level methods
      }
    };

    if (!state.searchQuery.value) {
      const filtered = state.prompts.value.filter(p => {
        if (!p || !p.method) {
          console.warn('⚠️ Invalid prompt object found:', p);
          return false;
        }

        const result = shouldIncludePromptInFolder(p.method);
        console.log(`🗃️ Folder filter for "${p.method}" = ${result}`);
        return result;
      });

      console.log('🔢 Filtered prompts (no search):', filtered.length, filtered);
      return filtered;
    }

    const query = state.searchQuery.value.toLowerCase();
    const filtered = state.prompts.value.filter(p => {
      if (!p || !p.method || !p.message) {
        console.warn('⚠️ Invalid prompt object found:', p);
        return false;
      }

      const methodMatch = p.method.toLowerCase().includes(query);
      const messageMatch = p.message.toLowerCase().includes(query);
      console.log(`🔍 Search filter: "${p.method}" matches "${query}" = ${methodMatch || messageMatch}`);

      return (methodMatch || messageMatch) && shouldIncludePromptInFolder(p.method);
    });

    console.log('🔢 Filtered prompts (with search):', filtered.length, filtered);
    return filtered;
  });

  // Group prompts by method
  const groupedPrompts = computed(() => {
    console.log('🔄 Computing groupedPrompts with filteredPrompts:', filteredPrompts.value);

    const groupedByMethod: Record<string, { currentPrompt: any, otherPrompts: any[] }> = {};

    if (!filteredPrompts.value || filteredPrompts.value.length === 0) {
      console.log('⚠️ No filtered prompts to group');
      return groupedByMethod;  // Return empty object
    }

    // Process all prompts to find folders
    const methods = new Set<string>();
    const folders = new Set<string>();

    console.log('🚀 Start processing filteredPrompts:', filteredPrompts.value.length);

    // First pass: identify all folders and methods in the current context
    filteredPrompts.value.forEach((prompt, index) => {
      try {
        if (!prompt || !prompt.method) {
          console.warn(`⚠️ Invalid prompt at index ${index}:`, prompt);
          return;
        }

        console.log(`📝 Processing prompt ${index}:`, prompt.method);
        let displayMethod = prompt.method;

        // Handle folder structure
        if (state.currentFolder.value) {
          // We're in a folder, so we need to get relative path
          if (prompt.method.startsWith(state.currentFolder.value + '/')) {
            displayMethod = prompt.method.substring(state.currentFolder.value.length + 1);
            console.log(`📁 In folder "${state.currentFolder.value}", relative method: "${displayMethod}"`);
          } else {
            // If not starting with the current folder path, skip this prompt
            console.log(`⏭️ Skipping prompt not in current folder: "${prompt.method}"`);
            return;
          }
        }

        // Check if this is a subfolder
        if (displayMethod.includes('/')) {
          const folderName = displayMethod.split('/')[0];
          folders.add(folderName);
          console.log(`📂 Added subfolder: "${folderName}" from method "${displayMethod}"`);
        } else {
          methods.add(displayMethod);
          console.log(`📄 Added method: "${displayMethod}"`);
        }
      } catch (error) {
        console.error('❌ Error processing prompt:', prompt, error);
      }
    });

    console.log('📊 Methods and folders parsed:', {
      methods: Array.from(methods),
      folders: Array.from(folders),
      methodCount: methods.size,
      folderCount: folders.size
    });

    // Create folder entries
    folders.forEach(folder => {
      try {
        const folderPath = state.currentFolder.value ? `${state.currentFolder.value}/${folder}` : folder;
        groupedByMethod[folder] = {
          currentPrompt: {
            id: `folder_${folderPath}`,
            method: folder,
            state: 'FOLDER',
            message: '',
            language: '',
            createdTime: 0,
            updatedTime: 0
          },
          otherPrompts: []
        };
        console.log(`📁 Created folder entry for "${folder}", path: "${folderPath}"`);
      } catch (error) {
        console.error('❌ Error creating folder entry:', folder, error);
      }
    });

    // Process regular prompts
    methods.forEach(method => {
      try {
        console.log(`🔍 Processing method: "${method}"`);
        const fullMethodPath = state.currentFolder.value ? `${state.currentFolder.value}/${method}` : method;

        const promptsForMethod = filteredPrompts.value
          .filter(p => {
            try {
              // Direct match for the full method path
              return p.method === fullMethodPath;
            } catch (e) {
              console.error('❌ Error filtering prompt by method:', p, e);
              return false;
            }
          })
          .sort((a, b) => {
            try {
              return (b.updatedTime || 0) - (a.updatedTime || 0);
            } catch (e) {
              console.error('❌ Error sorting prompts:', a, b, e);
              return 0;
            }
          });

        console.log(`📊 Prompts for method "${method}" (full path: "${fullMethodPath}"):`, promptsForMethod.length);

        if (promptsForMethod.length > 0) {
          const [currentPrompt, ...otherPrompts] = promptsForMethod;
          groupedByMethod[method] = {
            currentPrompt,
            otherPrompts
          };
          console.log(`✅ Added group for method "${method}" with ${otherPrompts.length + 1} prompts`);
        } else {
          console.warn(`⚠️ No prompts found for method "${method}"`);
        }
      } catch (error) {
        console.error('❌ Error processing method:', method, error);
      }
    });

    console.log('🏁 Final grouped prompts:', Object.keys(groupedByMethod).length, groupedByMethod);
    return groupedByMethod;
  });

  // Check if all prompts are selected
  const isAllSelected = computed(() => {
    try {
      if (!groupedPrompts.value || Object.keys(groupedPrompts.value).length === 0) {
        return false;
      }

      const selectablePrompts = Object.values(groupedPrompts.value)
        .filter(group => group && group.currentPrompt && group.currentPrompt.state !== 'FOLDER')
        .flatMap(group => {
          try {
            return [group.currentPrompt, ...(group.otherPrompts || [])];
          } catch (e) {
            console.error('Error flattening group:', group, e);
            return [];
          }
        })
        .filter(prompt => prompt && prompt.id)
        .map(prompt => prompt.id);

      console.log('Selectable prompts:', selectablePrompts);

      return selectablePrompts.length > 0 &&
        selectablePrompts.every(id => state.selectedPrompts.value.includes(id));
    } catch (error) {
      console.error('Error in isAllSelected computed property:', error);
      return false;
    }
  });

  // Check if response is a checker response
  const isCheckerResponse = computed(() => {
    try {
      const result = JSON.parse(state.response.value);
      return Object.prototype.hasOwnProperty.call(result, 'correctMessage') &&
        Object.prototype.hasOwnProperty.call(result, 'result');
    } catch {
      return false;
    }
  });

  // Format the response result
  const formattedResult = computed(() => {
    if (isCheckerResponse.value) {
      try {
        const parsed = JSON.parse(state.response.value);
        return parsed.result || '';
      } catch {
        return state.response.value;
      }
    }

    // If we have a pre-formatted JSON result, use it
    if (state.responseData.value && state.responseData.value.formattedResult) {
      return state.responseData.value.formattedResult;
    }

    // Otherwise try to format it if it's JSON
    try {
      if (isJSON(state.response.value)) {
        return formatJSON(JSON.parse(state.response.value));
      }
    } catch (error) {
      console.warn("Error formatting result as JSON:", error);
    }

    return state.response.value;
  });

  // Format the correct message for checker responses
  const formattedCorrectMessage = computed(() => {
    if (isCheckerResponse.value) {
      try {
        const parsed = JSON.parse(state.response.value);
        return parsed.correctMessage || '';
      } catch {
        return '';
      }
    }
    return '';
  });

  // Format the fixes for checker responses
  const formattedFixes = computed(() => {
    if (isCheckerResponse.value) {
      try {
        const parsed = JSON.parse(state.response.value);
        return parsed.fixes || '';
      } catch {
        return '';
      }
    }
    return '';
  });

  // Check if result is different from correct message
  const isResultDifferent = computed(() => {
    if (isCheckerResponse.value) {
      try {
        const parsed = JSON.parse(state.response.value);
        return parsed.result !== parsed.correctMessage;
      } catch {
        return false;
      }
    }
    return false;
  });

  // Format context JSON
  const formattedContextJson = computed(() => {
    // If we have a pre-formatted contextJson, use it
    if (state.responseData.value && state.responseData.value.formattedContextJson) {
      return state.responseData.value.formattedContextJson;
    }

    // Otherwise format it
    try {
      if (state.responseData.value && state.responseData.value.contextJson) {
        if (typeof state.responseData.value.contextJson === 'string') {
          // Try to parse if it's a string
          return formatJSON(JSON.parse(state.responseData.value.contextJson));
        } else {
          // Format if it's already an object
          return formatJSON(state.responseData.value.contextJson);
        }
      }
    } catch (e) {
      console.warn('Error formatting contextJson:', e);
    }

    // Fallback to basic stringification
    try {
      return JSON.stringify(state.responseData.value.contextJson, null, 2);
    } catch {
      return state.responseData.value.contextJson || '';
    }
  });

  // Check if result contains HTML formatting
  const resultIsFormatted = computed(() => {
    return typeof formattedResult.value === 'string' &&
      (formattedResult.value.includes('<span class="json-') ||
        formattedResult.value.includes('<span class="prompt-variable">'));
  });

  // Check if context JSON contains HTML formatting
  const contextIsFormatted = computed(() => {
    return typeof formattedContextJson.value === 'string' &&
      formattedContextJson.value.includes('<span class="json-');
  });

  // Compute highlighted prompt with variables highlighted
  const highlightedPrompt = computed(() => {
    return highlightVariables(state.promptForm.value.message || '');
  });

  // Check if prompt is for image generation
  const isImagePrompt = (text: string) => {
    if (!text) return false;
    
    // Check if message starts with "image:" prefix which indicates image generation
    if (typeof text === 'string' && text.trim().toLowerCase().startsWith('image:')) {
      return true;
    }
    
    return false;
  };

  return {
    filteredPrompts,
    groupedPrompts,
    isAllSelected,
    isCheckerResponse,
    formattedResult,
    formattedCorrectMessage,
    formattedFixes,
    isResultDifferent,
    formattedContextJson,
    resultIsFormatted,
    contextIsFormatted,
    highlightedPrompt,
    isImagePrompt
  };
}

export function useTestRunComputed(testRunState: TestRunState) {
  // Determine if a test run can be created based on form state
  const canCreateTestRun = computed(() => {
    if (!testRunState.selectedTestSetId.value || !testRunState.newTestRun.value.name) {
      return false;
    }

    // Ensure model ID or workflow is set based on execution method
    if (testRunState.executionMethodType.value === 'modelId' && !testRunState.newTestRun.value.modelId) {
      return false;
    }

    if (testRunState.executionMethodType.value === 'workflow' && !testRunState.newTestRun.value.workflow) {
      return false;
    }

    return true;
  });

  return {
    canCreateTestRun
  };
}