// Types used in the Traces tab components

// Define custom interface for traces group with chat metadata
export interface TracesGroup extends Array<any> {
    contextIds?: string[];
    representativeTraces?: any[];
    minTimestamp?: number;
    maxTimestamp?: number;
}

export interface MessageTaskType {
    promptIds?: string | string[];
    promptId?: string;
    message?: string;
    result?: string;
    messageId?: string;
    purpose?: string;
    contextId?: string;
    chatId?: string;
    imageTaskId?: string;
}

export interface TracesPage {
    content: any[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface ContextDetails {
    firstContextId?: string;
    lastContextId?: string;
    firstTrace?: any;
    lastTrace?: any;
    messageTask?: MessageTaskType;
}

export interface TraceStep {
    contextId: string;
    traceId: string;
}