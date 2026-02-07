const MAX_FILE_SIZE = 31_457_280; // 30MB
const ALLOWED_EXTENSIONS = ['pdf', 'txt', 'md'];
const FILE_NAME_REGEX = /^[가-힣a-zA-Z0-9_-]{1,30}$/;

export const validateFile = (file) => {
    const dotIndex = file.name.lastIndexOf('.');
    if (dotIndex === -1) {
        return '지원하지 않는 파일 형식입니다. (.pdf, .txt, .md만 가능)';
    }

    const extension = file.name.slice(dotIndex + 1).toLowerCase();
    const baseName = file.name.slice(0, dotIndex).normalize('NFC');

    if (!ALLOWED_EXTENSIONS.includes(extension)) {
        return '지원하지 않는 파일 형식입니다. (.pdf, .txt, .md만 가능)';
    }

    if (!FILE_NAME_REGEX.test(baseName)) {
        return '파일명은 한글, 영문, 숫자, _, -만 사용 가능합니다. (최대 30자)';
    }

    if (file.size > MAX_FILE_SIZE) {
        return '파일 크기는 30MB를 초과할 수 없습니다.';
    }

    if (file.size === 0) {
        return '빈 파일은 업로드할 수 없습니다.';
    }

    return null;
};

export const ACCEPT_EXTENSIONS = ALLOWED_EXTENSIONS.map((ext) => `.${ext}`).join(',');
