/**
 * Utility functions for formatting text, JSON, and highlighting variables
 */

/**
 * Checks if a string is valid JSON object or array
 * @param str - String to check
 * @returns true if the string is valid JSON object or array, false otherwise
 */
export const isJSON = (str: string): boolean => {
  try {
    const parsed = JSON.parse(str);
    // Make sure it's an object or array, not just a primitive
    return parsed && typeof parsed === 'object';
  } catch (e) {
    return false;
  }
};

/**
 * Pretty-prints JSON with proper indentation but no syntax highlighting
 * @param obj - JSON object or string to format
 * @returns Formatted JSON string
 */
export const prettyPrintJSON = (obj: any): string => {
  try {
    if (typeof obj === 'string') {
      // If it's already a string, try to parse it first to ensure valid JSON
      return JSON.stringify(JSON.parse(obj), null, 2);
    }
    // Otherwise stringify the object with indentation
    return JSON.stringify(obj, null, 2);
  } catch (e) {
    console.error('Error pretty-printing JSON:', e);
    return String(obj);
  }
};

/**
 * Formats JSON with syntax highlighting
 * @param obj - JSON object or string to format
 * @returns HTML string with syntax highlighting
 */
export const formatJSON = (obj: any): string => {
  try {
    // Format with proper escaping and color highlighting
    const json = typeof obj === 'string' ? obj : JSON.stringify(obj, null, 2);
    
    // First escape HTML special characters to prevent injection
    let result = json
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
      
    // Then apply color formatting with careful regex patterns
    // Pattern for keys with proper boundaries
    result = result.replace(/("(?:\\.|[^"\\])*")(\s*:)/g, '<span class="json-key">$1</span>$2');

    // String values - exclude any spans that were already created for keys
    // Use a more specific regex that ignores anything inside span tags
    result = result.replace(/("(?:\\.|[^"\\])*")(?!(\s*:|\w|.*<\/span>))/g, '<span class="json-string">$1</span>');

    // Numbers
    result = result.replace(/\b([-+]?\d+(\.\d+)?([eE][-+]?\d+)?)\b/g, '<span class="json-number">$1</span>');
    
    // Booleans and null
    result = result.replace(/\b(true|false|null)\b/g, '<span class="json-boolean">$1</span>');
    
    return result;
  } catch (e) {
    console.error('Error formatting JSON:', e);
    return String(obj);
  }
};

/**
 * Highlights template variables in the format {{variableName}}
 * @param text - Text containing variables to highlight
 * @returns HTML string with highlighted variables
 */
export const highlightVariables = (text: string): string => {
  if (!text) return '';
  
  // Escape HTML special characters to prevent injection
  let result = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  
  // Highlight variables in the {{variableName}} format
  result = result.replace(/\{\{([^{}]+)\}\}/g, '<span class="prompt-variable">{{$1}}</span>');
  
  // Convert new lines to HTML line breaks
  result = result.replace(/\n/g, '<br>');
  
  return result;
};

/**
 * Add these styles to your global CSS or component where you're using these functions
 */
export const formattingStyles = `
/* JSON formatting styles */
.json-key {
  color: #f92672;
}
.json-string {
  color: #a6e22e;
}
.json-number {
  color: #ae81ff;
}
.json-boolean {
  color: #66d9ef;
}

/* Variable highlighting styles */
.prompt-variable {
  color: #ff9900;
  font-weight: bold;
  background-color: rgba(255, 153, 0, 0.1);
  padding: 2px;
  border-radius: 3px;
}
`;