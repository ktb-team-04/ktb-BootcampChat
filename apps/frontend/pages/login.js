import { useEffect } from 'react';
import LoginPage from './index';

/**
 * `/login`을 사용하는 기존 클라이언트와 부하 시나리오를 지원한다.
 * 화면 전환을 다시 일으키지 않고 주소만 정식 로그인 URL(`/`)로 정규화해
 * 연속된 인증 요청 사이의 navigation abort를 피한다.
 */
export default function LoginAliasPage() {
  useEffect(() => {
    window.history.replaceState(window.history.state, '', `/${window.location.search}`);
  }, []);

  return <LoginPage />;
}
