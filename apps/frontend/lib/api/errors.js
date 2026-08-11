import axios from 'axios';

export const RETRY_CONFIG = {
  maxRetries: 2,
  initialDelayMs: 500,
  maxDelayMs: 2000,
  backoffFactor: 2,
  retryableStatuses: [408, 429, 500, 502, 503, 504],
  retryableErrors: [
    'ECONNABORTED',
    'ETIMEDOUT',
    'ENOTFOUND',
    'ENETUNREACH',
    'ERR_NETWORK',
  ],
};

export const getRetryDelay = (retryCount, retryAfter) => {
  const retryAfterSeconds = Number(retryAfter);
  if (Number.isFinite(retryAfterSeconds) && retryAfterSeconds > 0) {
    return retryAfterSeconds * 1000;
  }

  const maximumDelay = Math.min(
    RETRY_CONFIG.initialDelayMs *
      Math.pow(RETRY_CONFIG.backoffFactor, retryCount),
    RETRY_CONFIG.maxDelayMs
  );

  // full jitter로 장애 복구 직후 모든 클라이언트가 동시에 재요청하는 현상을 줄인다.
  return Math.random() * maximumDelay;
};

export const isRetryAllowed = (config = {}) => {
  const method = config.method?.toLowerCase();
  return ['get', 'head', 'options'].includes(method) || config.allowRetry === true;
};

export const isRetryableError = (error) => {
  if (!error) {
    return false;
  }

  if (error.code && RETRY_CONFIG.retryableErrors.includes(error.code)) {
    return true;
  }

  if (error.response?.status && RETRY_CONFIG.retryableStatuses.includes(error.response.status)) {
    return true;
  }

  return !error.response && error.request;
};

export const createAuthExpiredError = (error, config) => {
  const authError = new Error('인증이 만료되었습니다. 다시 로그인해주세요.');
  authError.status = 401;
  authError.code = 'AUTH_EXPIRED';
  authError.config = config;
  authError.originalError = error;
  return authError;
};

export const createNetworkError = (error, config, retry) => {
  const customError = new Error(
    [
      '서버와 통신할 수 없습니다.',
      '네트워크 연결을 확인하고 잠시 후 다시 시도해주세요.',
      error.code ? `(Error: ${error.code})` : '',
    ]
      .filter(Boolean)
      .join(' ')
  );

  customError.isNetworkError = true;
  customError.originalError = error;
  customError.status = 0;
  customError.code = error.code || 'NETWORK_ERROR';
  customError.config = config;
  customError.retry = retry;
  return customError;
};

export const createHttpError = (error, config, retry) => {
  const status = error.response.status;
  const errorData = error.response.data;
  let errorMessage;

  switch (status) {
    case 400:
      errorMessage = errorData?.message || '잘못된 요청입니다.';
      break;
    case 401:
      errorMessage = '인증이 필요하거나 만료되었습니다.';
      break;
    case 403:
      errorMessage = errorData?.message || '접근 권한이 없습니다.';
      break;
    case 404:
      errorMessage = errorData?.message || '요청한 리소스를 찾을 수 없습니다.';
      break;
    case 408:
      errorMessage = '요청 시간이 초과되었습니다.';
      break;
    case 429:
      errorMessage = '너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.';
      break;
    case 500:
      errorMessage = '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
      break;
    case 502:
    case 503:
    case 504:
      errorMessage = '서버가 일시적으로 응답할 수 없습니다. 잠시 후 다시 시도해주세요.';
      break;
    default:
      errorMessage = errorData?.message || '예기치 않은 오류가 발생했습니다.';
  }

  const enhancedError = new Error(errorMessage);
  enhancedError.status = status;
  enhancedError.code = errorData?.code;
  enhancedError.data = errorData;
  enhancedError.config = config;
  enhancedError.originalError = error;
  enhancedError.retry = retry;
  return enhancedError;
};

export const isCanceledRequest = (error) => axios.isCancel(error);
