export const TURN_STATUS_LABEL = {
    COMPLETED: '완료',
    IRRELEVANT: '관련 없음',
    FAILED: '실패',
};

export const MAX_FILES = 30;
export const MAX_STORAGE_MB = 300;

export const DOCUMENT_STATUS = {
    INITIATED: { label: '업로드 대기', polling: false },
    UPLOADED: { label: '분석 대기', polling: true },
    PROCESSING: { label: '처리 중', polling: true },
    COMPLETED: { label: '준비됨', polling: false },
    FAILED: { label: '실패', polling: false },
};
