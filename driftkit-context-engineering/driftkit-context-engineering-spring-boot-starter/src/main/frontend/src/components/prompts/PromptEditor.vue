<template>
  <div class="card mb-4">
    <div class="card-header">
      <h5 class="m-0 font-semibold">Prompt Editor</h5>
    </div>
    <div class="card-content p-4">
      <!-- Method -->
      <div class="field mb-3">
        <label class="field-label">Method</label>
        <InputText :modelValue="promptForm.method" @update:modelValue="updateField('method', $event)" placeholder="Enter prompt method (e.g. reports/monthly)" class="w-full" />
        <small class="text-muted">This identifier will be used when saving the prompt.</small>
      </div>

      <!-- System Message -->
      <div class="field mb-3">
        <label class="field-label">System Message</label>
        <Textarea :modelValue="promptForm.systemMessage" @update:modelValue="updateField('systemMessage', $event)" rows="3" placeholder="Enter system message (optional)" class="w-full" />
      </div>

      <!-- Prompt Template -->
      <div class="field mb-3">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <label class="field-label m-0">Prompt Template</label>
          <div class="d-flex align-items-center gap-2">
            <label class="text-sm">Preview variables</label>
            <ToggleButton :modelValue="showPreview" @update:modelValue="$emit('update:showPreview', $event)" onLabel="On" offLabel="Off" />
          </div>
        </div>
        <Textarea
          v-if="!showPreview"
          :modelValue="promptForm.message"
          @update:modelValue="updateField('message', $event)"
          rows="12"
          placeholder="Enter your prompt template here. Use {{variable_name}} for template variables."
          class="w-full font-mono"
        />
        <div
          v-else
          class="preview-box"
          @click="$emit('update:showPreview', false)"
          v-html="highlightedPrompt"
        ></div>
        <small class="text-muted">Click on the preview to switch back to editing mode.</small>
      </div>

      <!-- Variables -->
      <div v-if="promptForm.variables.length > 0" class="field mb-3">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <label class="field-label m-0">Variables</label>
          <Button
            v-if="promptForm.method"
            label="Load Random Example"
            icon="pi pi-refresh"
            size="small"
            severity="secondary"
            outlined
            :loading="loadingRandomTask"
            @click="$emit('load-random')"
          />
        </div>
        <div v-for="variable in promptForm.variables" :key="variable" class="mb-2">
          <label class="text-sm text-muted">{{ variable }}</label>
          <InputText :modelValue="promptForm.variableValues[variable]" @update:modelValue="updateVariable(variable, $event)" class="w-full" />
        </div>
      </div>

      <!-- Variable Schema Editor -->
      <div v-if="promptForm.variables.length > 0" class="field mb-3">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <label class="field-label m-0">Variable Schema</label>
          <ToggleButton :modelValue="showSchema" @update:modelValue="showSchema = $event" onLabel="Hide Schema" offLabel="Edit Schema" />
        </div>
        <div v-if="showSchema" class="schema-grid">
          <div v-for="variable in promptForm.variables" :key="'schema-'+variable" class="schema-row">
            <span class="schema-name">{{ variable }}</span>
            <Select :modelValue="getSchemaField(variable, 'type')" @update:modelValue="setSchemaField(variable, 'type', $event)" :options="['string','number','json','text']" placeholder="Type" class="schema-field" />
            <div class="d-flex align-items-center gap-1">
              <Checkbox :binary="true" :modelValue="getSchemaField(variable, 'required')" @update:modelValue="setSchemaField(variable, 'required', $event)" :inputId="'req-'+variable" />
              <label :for="'req-'+variable" class="text-xs">Req</label>
            </div>
            <InputText :modelValue="getSchemaField(variable, 'defaultValue')" @update:modelValue="setSchemaField(variable, 'defaultValue', $event)" placeholder="Default" class="schema-field" />
            <InputText :modelValue="getSchemaField(variable, 'description')" @update:modelValue="setSchemaField(variable, 'description', $event)" placeholder="Description" class="schema-field" />
          </div>
        </div>
      </div>

      <!-- Settings Row -->
      <div class="settings-grid mb-3">
        <div class="field">
          <label class="field-label">Workflow</label>
          <Select :modelValue="promptForm.workflow" @update:modelValue="updateField('workflow', $event)" :options="workflowOptions" optionLabel="label" optionValue="value" placeholder="None" class="w-full" />
        </div>
        <div class="field">
          <label class="field-label">Model ID</label>
          <InputText :modelValue="promptForm.modelId" @update:modelValue="updateField('modelId', $event)" class="w-full" />
        </div>
        <div class="field">
          <label class="field-label">Temperature</label>
          <InputNumber :modelValue="promptForm.temperature" @update:modelValue="updateField('temperature', $event)" :min="0" :max="2" :step="0.01" placeholder="Default" class="w-full" />
        </div>
        <div class="field">
          <label class="field-label">Language</label>
          <Select :modelValue="promptForm.language" @update:modelValue="updateField('language', $event)" :options="languageOptions" class="w-full" />
        </div>
      </div>

      <!-- Purpose -->
      <div class="field mb-3">
        <label class="field-label">Purpose</label>
        <InputText :modelValue="promptForm.purpose" @update:modelValue="updateField('purpose', $event)" placeholder="Optional request purpose" class="w-full" />
      </div>

      <!-- Save Options -->
      <div class="d-flex flex-wrap gap-3 mb-3">
        <div class="d-flex align-items-center gap-1">
          <Checkbox :modelValue="savePrompt" :binary="true" @update:modelValue="$emit('update:savePrompt', $event)" inputId="savePrompt" />
          <label for="savePrompt" class="text-sm">Save Prompt</label>
        </div>
        <div class="d-flex align-items-center gap-1">
          <Checkbox :modelValue="saveForAllLanguages" :binary="true" @update:modelValue="$emit('update:saveForAllLanguages', $event)" inputId="saveAll" />
          <label for="saveAll" class="text-sm">Save for existing languages</label>
        </div>
        <div class="d-flex align-items-center gap-1">
          <Checkbox :modelValue="promptForm.jsonRequest" :binary="true" @update:modelValue="updateField('jsonRequest', $event)" inputId="jsonReq" />
          <label for="jsonReq" class="text-sm">JSON Request</label>
        </div>
        <div class="d-flex align-items-center gap-1">
          <Checkbox :modelValue="promptForm.jsonResponse" :binary="true" @update:modelValue="updateField('jsonResponse', $event)" inputId="jsonResp" />
          <label for="jsonResp" class="text-sm">JSON Response</label>
        </div>
      </div>

      <!-- Actions -->
      <div class="d-flex flex-wrap gap-2">
        <Button label="Execute" icon="pi pi-play" @click="$emit('execute')" />
        <Button label="Save Draft" icon="pi pi-save" severity="secondary" @click="$emit('save')" />
        <Button label="Publish Directly" icon="pi pi-check" severity="success" @click="$emit('publish')" />
        <Button label="Submit for Testing" icon="pi pi-flask" severity="info" @click="$emit('submit-for-testing')" />
        <Button label="Run With Test Set" icon="pi pi-cog" severity="secondary" outlined @click="$emit('open-test-runs')" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Select from 'primevue/select';
import InputNumber from 'primevue/inputnumber';
import Checkbox from 'primevue/checkbox';
import Button from 'primevue/button';
import ToggleButton from 'primevue/togglebutton';

const props = defineProps<{
  promptForm: any;
  workflows: any[];
  showPreview: boolean;
  highlightedPrompt: string;
  loadingRandomTask: boolean;
  savePrompt: boolean;
  saveForAllLanguages: boolean;
}>();

const emit = defineEmits([
  'update:promptForm', 'update:showPreview', 'update:savePrompt', 'update:saveForAllLanguages',
  'execute', 'save', 'publish', 'submit-for-testing', 'open-test-runs', 'load-random',
]);

const updateField = (field: string, value: any) => {
  // promptForm is a reactive ref passed by reference - direct mutation updates the source
  props.promptForm[field] = value;
};

const updateVariable = (variable: string, value: string) => {
  props.promptForm.variableValues[variable] = value;
};

const workflowOptions = computed(() => [
  { label: 'None', value: '' },
  ...props.workflows.map((w: any) => ({ label: `${w.name} - ${w.description}`, value: w.id })),
]);

const languageOptions = ['GENERAL', 'SPANISH', 'ENGLISH'];

// Variable Schema editor
const showSchema = ref(false);

const getSchemaField = (variable: string, field: string): any => {
  const schemas = props.promptForm.variableSchemas || [];
  const schema = schemas.find((s: any) => s.name === variable);
  return schema ? schema[field] : (field === 'required' ? false : '');
};

const setSchemaField = (variable: string, field: string, value: any) => {
  if (!props.promptForm.variableSchemas) props.promptForm.variableSchemas = [];
  let schema = props.promptForm.variableSchemas.find((s: any) => s.name === variable);
  if (!schema) {
    schema = { name: variable, type: 'string', required: false, defaultValue: '', description: '', example: '' };
    props.promptForm.variableSchemas.push(schema);
  }
  schema[field] = value;
};
</script>

<style scoped>
.card { background: var(--p-surface-card); border: 1px solid var(--p-surface-border); border-radius: 8px; overflow: hidden; }
.card-header { padding: 1rem 1.25rem; border-bottom: 1px solid var(--p-surface-border); background: var(--p-surface-50); }
.card-content { padding: 1.25rem; }
.field { display: flex; flex-direction: column; gap: 0.375rem; }
.field-label { font-size: 0.875rem; font-weight: 500; color: var(--p-text-color); }
.settings-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; }
.preview-box { min-height: 200px; max-height: 400px; overflow: auto; white-space: pre-wrap; font-family: monospace; font-size: 0.875rem; padding: 0.75rem; border: 1px solid var(--p-surface-border); border-radius: 6px; background: var(--p-surface-50); cursor: pointer; transition: background 0.15s; }
.preview-box:hover { background: var(--p-surface-100); }
.font-mono { font-family: monospace; }
.schema-grid { display: flex; flex-direction: column; gap: 0.5rem; }
.schema-row { display: flex; align-items: center; gap: 0.5rem; }
.schema-name { font-weight: 600; font-size: 0.85rem; min-width: 100px; font-family: monospace; }
.schema-field { min-width: 100px; flex: 1; }
.text-xs { font-size: 0.75rem; }
.d-flex { display: flex; }
.flex-wrap { flex-wrap: wrap; }
.justify-content-between { justify-content: space-between; }
.align-items-center { align-items: center; }
.gap-1 { gap: 0.25rem; }
.gap-2 { gap: 0.5rem; }
.gap-3 { gap: 0.75rem; }
.m-0 { margin: 0; }
.mb-2 { margin-bottom: 0.5rem; }
.mb-3 { margin-bottom: 1rem; }
.mb-4 { margin-bottom: 1.5rem; }
.p-4 { padding: 1.25rem; }
.w-full { width: 100%; }
.text-sm { font-size: 0.875rem; }
.text-muted { color: var(--p-text-muted-color); }
.font-semibold { font-weight: 600; }
:deep(.prompt-variable) { color: #ff9900; font-weight: bold; background-color: rgba(255, 153, 0, 0.1); padding: 2px; border-radius: 3px; }
@media (max-width: 768px) { .settings-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
