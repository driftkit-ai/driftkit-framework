import axios from 'axios';
import { PromptsState } from './types';
import { Ref } from 'vue';

export function usePromptMethods(state: PromptsState, emit: any) {
  // Fetch all prompts
  const fetchPrompts = () => {
    state.loading.value = true;

    const creds = sessionStorage.getItem('credentials');
    axios.get('/data/v1.0/admin/prompt/', {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
      if (response.data && response.data.data) {
        const apiData = response.data.data;

        if (Array.isArray(apiData)) {
          if (apiData.length > 0) {
            // Deep clone the array to ensure we're not keeping any references
            const promptsArray = JSON.parse(JSON.stringify(apiData));

            // Sort the prompts
            promptsArray.sort((a: any, b: any) => a.method.localeCompare(b.method));

            // Set to the ref
            state.prompts.value = promptsArray;
          } else {
            console.warn('API returned empty array for prompts');
            state.prompts.value = [];
          }
        } else {
          console.error('API data is not an array:', apiData);
          state.prompts.value = [];
        }
      } else {
        console.error('Unexpected response format:', response.data);
        state.prompts.value = [];
      }
    }).catch(error => {
      console.error('Error fetching prompts:', error);
      state.prompts.value = [];
    }).finally(() => {
      state.loading.value = false;
    });
  };

  // Fetch available workflows
  const fetchWorkflows = () => {
    const creds = sessionStorage.getItem('credentials');
    axios.get('/data/v1.0/admin/workflows', {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
      if (response.data && response.data.data) {
        state.workflows.value = response.data.data;
      } else if (Array.isArray(response.data)) {
        state.workflows.value = response.data;
      } else {
        console.warn('Unexpected workflows response format:', response.data);
        state.workflows.value = [];
      }
    }).catch(error => {
      console.error('Error fetching workflows:', error);
      state.workflows.value = [];
    });
  };

  // Select a prompt by ID
  const selectPrompt = (id: string) => {
    if (!id) {
      console.warn('Cannot select prompt: Empty ID provided');
      return;
    }

    // Update selected ID and emit events
    state.selectedPromptIdRef.value = id;
    emit('update:selectedPromptId', id);
    emit('prompt-selected', id);

    // Find the prompt in the array
    const selectedPrompt = state.prompts.value.find(p => p.id === id);

    if (selectedPrompt) {
      // Update form fields
      state.promptForm.value.method = selectedPrompt.method;
      state.promptForm.value.message = selectedPrompt.message;
      state.promptForm.value.systemMessage = selectedPrompt.systemMessage || '';
      state.promptForm.value.workflow = selectedPrompt.workflow || '';
      state.promptForm.value.modelId = selectedPrompt.modelId || '';
      state.promptForm.value.temperature = selectedPrompt.temperature !== undefined ? selectedPrompt.temperature : null;

      // Convert language to uppercase to ensure correct selection in dropdown
      const promptLanguage = selectedPrompt.language || 'GENERAL';
      state.promptForm.value.language = promptLanguage.toUpperCase();

      state.promptForm.value.jsonRequest = selectedPrompt.jsonRequest || false;
      state.promptForm.value.jsonResponse = selectedPrompt.jsonResponse || false;
      state.promptForm.value.purpose = selectedPrompt.purpose || 'prompt_engineering';

      // Extract variables from the prompt template
      const variablePattern = /\{\{\s*([\w.]+)\s*\}\}/g;
      let match;
      const variables = new Set<string>();

      while ((match = variablePattern.exec(selectedPrompt.message)) !== null) {
        variables.add(match[1]);
      }

      state.promptForm.value.variables = Array.from(variables);
      state.promptForm.value.variableValues = {};

      // Initialize variable values
      state.promptForm.value.variables.forEach(variable => {
        state.promptForm.value.variableValues[variable] = '';
      });

    } else {
      console.error(`Cannot find prompt with ID "${id}" in prompts array`);
    }
  };

  // Toggle expansion of a method
  const toggleMethod = (method: string) => {
    if (state.expandedMethods.value.includes(method)) {
      state.expandedMethods.value = state.expandedMethods.value.filter(m => m !== method);
    } else {
      state.expandedMethods.value.push(method);
    }
  };

  // Open a folder
  const openFolder = (folder: string) => {
    // If in root, set folder directly. If in subfolder, append to the path
    state.currentFolder.value = state.currentFolder.value ? `${state.currentFolder.value}/${folder}` : folder;
    state.searchQuery.value = '';

    // Clear any selected prompts when changing folders
    state.selectedPrompts.value = [];
    state.expandedMethods.value = [];

  };

  // Go to root folder
  const goToRoot = () => {
    state.currentFolder.value = '';
    state.searchQuery.value = '';

    // Clear any selected prompts when returning to root
    state.selectedPrompts.value = [];
    state.expandedMethods.value = [];

  };

  // Format timestamp to readable date/time
  const formatTime = (timestamp: number) => {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleString();
  };

  // Toggle selection of all prompts
  const toggleAllSelection = (groupedPrompts: any) => {
    const isAllSelected = Object.values(groupedPrompts)
      .filter((group: any) => group.currentPrompt.state !== 'FOLDER')
      .flatMap((group: any) => [group.currentPrompt, ...group.otherPrompts])
      .map((prompt: any) => prompt.id)
      .every((id: string) => state.selectedPrompts.value.includes(id));

    if (isAllSelected) {
      state.selectedPrompts.value = [];
    } else {
      state.selectedPrompts.value = Object.values(groupedPrompts)
        .filter((group: any) => group.currentPrompt.state !== 'FOLDER')
        .flatMap((group: any) => [group.currentPrompt, ...group.otherPrompts])
        .map((prompt: any) => prompt.id);
    }
  };

  // Clear selection
  const clearSelection = () => {
    state.selectedPrompts.value = [];
  };

  // Delete a single prompt
  const deletePrompt = (prompt: any) => {
    if (confirm(`Are you sure you want to delete prompt "${prompt.method}"?`)) {
      state.loading.value = true;

      const creds = sessionStorage.getItem('credentials');
      axios.delete(`/data/v1.0/admin/prompt/${prompt.id}`, {
        headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
      }).then(() => {
        fetchPrompts();
      }).catch(error => {
        console.error('Error deleting prompt:', error);
      }).finally(() => {
        state.loading.value = false;
      });
    }
  };

  // Delete selected prompts
  const deleteSelectedPrompts = () => {
    if (state.selectedPrompts.value.length === 0) return;

    if (confirm(`Are you sure you want to delete ${state.selectedPrompts.value.length} selected prompts?`)) {
      state.loading.value = true;
      const creds = sessionStorage.getItem('credentials');

      // Delete prompts one by one
      const deletePromises = state.selectedPrompts.value.map(id => {
        return axios.delete(`/data/v1.0/admin/prompt/${id}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
      });

      Promise.all(deletePromises)
        .then(() => {
          state.selectedPrompts.value = [];
          fetchPrompts();
        })
        .catch(error => {
          console.error('Error deleting prompts:', error);
        })
        .finally(() => {
          state.loading.value = false;
        });
    }
  };

  // Execute a prompt
  const executePrompt = () => {
    if (!state.promptForm.value.method) {
      alert('Please enter a prompt ID.');
      return;
    }

    state.loading.value = true;
    state.response.value = '';
    state.responseData.value = {};

    // Ensure language is in uppercase
    const language = (state.promptForm.value.language || 'GENERAL').toUpperCase();

    const requestData: any = {
      promptIds: [
        {
          promptId: state.promptForm.value.method,
          prompt: state.promptForm.value.message,
          temperature: state.promptForm.value.temperature !== null ? state.promptForm.value.temperature : undefined
        }
      ],
      variables: state.promptForm.value.variableValues,
      savePrompt: state.savePrompt.value,
      language: language,
      purpose: state.promptForm.value.purpose || null
    };

    if (state.promptForm.value.workflow) {
      requestData.workflow = state.promptForm.value.workflow;
    }

    if (state.promptForm.value.modelId) {
      requestData.modelId = state.promptForm.value.modelId;
    }

    const creds = sessionStorage.getItem('credentials');
    axios.post('/data/v1.0/admin/llm/prompt/message/sync', requestData, {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(resp => {
      // Extract data from the nested structure
      if (resp.data && resp.data.data) {
        // Format the result text if it's JSON
        const resultText = resp.data.data.result || '';
        try {
          // Check if the result is valid JSON
          if (resultText && isJSON(resultText)) {
            // Parse and reformat JSON for better viewing
            state.response.value = resultText; // Keep raw JSON in response for other computed properties
            resp.data.data.formattedResult = formatJSON(JSON.parse(resultText)); // Add formatted version
          } else {
            state.response.value = resultText;
          }
        } catch (e) {
          console.warn('Failed to parse result as JSON:', e);
          state.response.value = resultText;
        }
        
        // Check if it's an image response and has no result
        if (resp.data.data.imageTaskId && !state.response.value && resp.data.data.message) {
          state.response.value = resp.data.data.message;
        }

        // Format contextJson if present
        if (resp.data.data.contextJson && typeof resp.data.data.contextJson === 'object') {
          try {
            resp.data.data.formattedContextJson = formatJSON(resp.data.data.contextJson);
          } catch (e) {
            console.warn('Failed to format contextJson:', e);
          }
        }

        // Store entire response data for rendering
        state.responseData.value = resp.data.data;
        
        // Special case for image tasks - ensure response is not empty
        if (resp.data.data.imageTaskId && !state.response.value) {
          state.response.value = "Image generated successfully";
        }
      } else {
        console.warn('Unexpected API response format:', resp.data);
        state.response.value = JSON.stringify(resp.data, null, 2);
        state.responseData.value = resp.data;
      }

      // If prompt was saved, refresh the prompts list
      if (state.savePrompt.value) {
        fetchPrompts();
      }
    }).catch(error => {
      console.error('Error executing prompt:', error);
      state.response.value = `Error: ${error.response?.data?.message || error.message}`;
    }).finally(() => {
      state.loading.value = false;
    });
  };

  // Function to check if a string is valid JSON
  const isJSON = (str: string) => {
    try {
      JSON.parse(str);
      return true;
    } catch (e) {
      return false;
    }
  };

  // Format JSON with syntax highlighting
  const formatJSON = (json: any): string => {
    // This is just a signature - the implementation comes from imported utils
    return '';
  };

  // Save prompt without executing
  const savePromptOnly = async () => {
    if (!state.promptForm.value.method) {
      alert('Please enter a prompt ID.');
      return;
    }

    state.loading.value = true;
    const creds = sessionStorage.getItem('credentials');

    try {
      // Ensure the language is in uppercase
      const currentLanguage = (state.promptForm.value.language || 'GENERAL').toUpperCase();

      // Create base request data without language field
      const baseRequestData = {
        method: state.promptForm.value.method, // Backend expects 'method', not 'promptId'
        message: state.promptForm.value.message,
        systemMessage: state.promptForm.value.systemMessage || '',
        state: 'CURRENT', // Set state explicitly
        workflow: state.promptForm.value.workflow || null,
        modelId: state.promptForm.value.modelId || null,
        temperature: state.promptForm.value.temperature !== null ? state.promptForm.value.temperature : undefined,
        jsonRequest: state.promptForm.value.jsonRequest,
        jsonResponse: state.promptForm.value.jsonResponse,
        createdTime: Date.now(),
        updatedTime: Date.now(),
        approvedTime: Date.now()
      };

      if (state.saveForAllLanguages.value) {
        // If saving for all languages, we need to find existing languages for this method
        const existingPrompts = state.prompts.value.filter(p => p.method === state.promptForm.value.method);

        // Get unique languages and ensure they're all uppercase
        const languages = [...new Set(existingPrompts.map(p => (p.language || 'GENERAL').toUpperCase()))];

        if (languages.length === 0) {
          // If no existing languages, just save with the current language
          languages.push(currentLanguage);
        }

        // Create promises for each language
        const savePromises = languages.map(language => {
          const requestData = {
            ...baseRequestData,
            language: language
          };

          return axios.post('/data/v1.0/admin/prompt/', requestData, {
            headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
          });
        });

        // Wait for all save operations to complete
        await Promise.all(savePromises);
        alert(`Prompt saved successfully for ${languages.length} languages!`);
      } else {
        // Save for current language only
        const requestData = {
          ...baseRequestData,
          language: currentLanguage
        };

        await axios.post('/data/v1.0/admin/prompt/', requestData, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
        alert('Prompt saved successfully!');
      }

      // Refresh prompts list
      fetchPrompts();
    } catch (error: any) { // Add type annotation to error
      console.error('Error saving prompt:', error);
      const errorMessage = error.response?.data?.message || error.message || 'Unknown error';
      alert(`Error saving prompt: ${errorMessage}`);
    } finally {
      state.loading.value = false;
    }
  };

  // Load random task variables
  const loadRandomTaskVariables = () => {
    if (!state.promptForm.value.method || state.promptForm.value.variables.length === 0) {
      return;
    }

    state.loadingRandomTask.value = true;

    // Get current prompt ID using method from the form
    const promptMethod = state.promptForm.value.method;

    const creds = sessionStorage.getItem('credentials');

    // First get the traces to find messageIds/contextIds that used this prompt
    axios.get(`/data/v1.0/analytics/traces?promptId=${encodeURIComponent(promptMethod)}&size=100`, {
      headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
    }).then(response => {
      if (response.data && response.data.data && response.data.data.content) {
        const traces = response.data.data.content;

        // Get unique context IDs (which are equivalent to messageIds)
        const contextIds = [...new Set(traces
          .filter((trace: { contextId?: string }) => trace.contextId)
          .map((trace: { contextId: string }) => trace.contextId)
        )];

        if (contextIds.length === 0) {
          state.loadingRandomTask.value = false;
          return;
        }

        // Pick a random context ID
        const randomIndex = Math.floor(Math.random() * Math.min(contextIds.length, 100));
        const randomContextId = contextIds[randomIndex];

        // Fetch the MessageTask using the random contextId
        return axios.get(`/data/v1.0/analytics/message-tasks?contextIds=${randomContextId}`, {
          headers: { ...(creds ? { Authorization: 'Basic ' + creds } : {}) }
        });
      } else {
        throw new Error('No traces found for this prompt method');
      }
    }).then(response => {
      if (response && response.data && response.data.data && response.data.data.length > 0) {
        const task = response.data.data[0];

        // Check if task has variables
        if (task.variables) {
          // Populate form variables from task
          state.promptForm.value.variables.forEach(variable => {
            if (task.variables[variable] !== undefined) {
              state.promptForm.value.variableValues[variable] = task.variables[variable];
            }
          });
        } else {
          alert('The selected task does not have any variables');
        }
      } else {
        throw new Error('No message task found');
      }
    }).catch(error => {
      console.error('Error loading random task variables:', error);
      alert('Error loading variables: ' + (error.response?.data?.message || error.message));
    }).finally(() => {
      state.loadingRandomTask.value = false;
    });
  };

  return {
    fetchPrompts,
    fetchWorkflows,
    selectPrompt,
    toggleMethod,
    openFolder,
    goToRoot,
    formatTime,
    toggleAllSelection,
    clearSelection,
    deletePrompt,
    deleteSelectedPrompts,
    executePrompt,
    savePromptOnly,
    loadRandomTaskVariables
  };
}