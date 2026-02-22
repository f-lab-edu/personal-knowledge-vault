export const STATUS_LABEL = {
    COMPLETED: '완료',
    IRRELEVANT: '관련없음',
    CANCELLED: '취소됨',
    FAILED: '실패',
};

export const MAX_FILES = 30;
export const MAX_STORAGE_MB = 300;

export const SOURCE_STATUS = {
    UPLOADED:   { label: '분석 대기', polling: true },
    PROCESSING: { label: '처리 중',  polling: true },
    COMPLETED:  { label: '준비됨',   polling: false },
    FAILED:     { label: '실패',     polling: false },
};
