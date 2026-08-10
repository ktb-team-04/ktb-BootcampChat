const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';

/**
 * 로그인 액션
 * @param {import('@playwright/test').Page} page
 * @param {Object} credentials - { email: string, password: string }
 * @param waitForRedirect
 */
async function loginAction(page, credentials, waitForRedirect = true) {
  // `/login`은 App Router에서 `/`로 즉시 리다이렉트하는 호환 경로다.
  // 부하 상황에서는 page.goto와 해당 리다이렉트가 경합할 수 있으므로
  // 실제 로그인 폼이 있는 정식 경로로 바로 이동한다.
  await page.goto(`${BASE_URL}/`);
  await page.getByTestId('login-email-input').fill(credentials.email);
  await page.getByTestId('login-password-input').fill(credentials.password);
  await page.getByTestId('login-submit-button').click();
  if (waitForRedirect) {
    await page.waitForURL(`${BASE_URL}/chat`);
  }
}

/**
 * 회원가입 액션
 * @param {import('@playwright/test').Page} page
 * @param {Object} userData - { email: string, password: string, passwordConfirm: string, name: string }
 */
async function registerAction(page, userData) {
  await page.goto(`${BASE_URL}/register`);
  await page.getByTestId('register-email-input').fill(userData.email);
  await page.getByTestId('register-password-input').fill(userData.password);
  await page.getByTestId('register-password-confirm-input').fill(userData.passwordConfirm);
  await page.getByTestId('register-name-input').fill(userData.name);
  await page.getByTestId('register-submit-button').click();

  // 성공/실패 응답이 화면에 반영될 때까지만 기다린다. 고정 sleep은 빠른
  // 응답에서도 VU를 붙잡고, 성공 후 예약된 `/login` 이동과 다음 액션을
  // 경합시킬 수 있다.
  await page.getByTestId('register-success-message')
    .or(page.getByTestId('register-error-message'))
    .waitFor({ state: 'visible' });
}

/**
 * 로그아웃 액션
 * @param {import('@playwright/test').Page} page
 */
async function logoutAction(page) {
  await page.getByTestId('logout-link').click();
}

module.exports = {
  loginAction,
  registerAction,
  logoutAction,
};
