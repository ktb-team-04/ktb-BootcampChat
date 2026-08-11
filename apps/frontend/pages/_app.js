import React from 'react';
import dynamic from 'next/dynamic';
import { useRouter } from 'next/router';
import { ThemeProvider } from '@vapor-ui/core';
import '@vapor-ui/core/styles.css';
import '../styles/globals.css';
import ChatHeader from '@/components/ChatHeader';
import ToastContainer from '@/components/Toast';
import { AuthProvider, useAuth } from '@/contexts/AuthContext';

const SocketProvider = dynamic(
  () => import('@/lib/socket/SocketProvider').then((mod) => mod.SocketProvider),
  { ssr: false }
);

const AuthenticatedSocketProvider = ({ children, enabled }) => {
  const { user } = useAuth();

  if (!enabled) {
    return children;
  }

  return <SocketProvider session={user}>{children}</SocketProvider>;
};

function MyApp({ Component, pageProps }) {
  const router = useRouter();

  const isErrorPage = router.pathname === '/_error';
  if (isErrorPage) {
    return <Component {...pageProps} />;
  }

  // 로그인/회원가입 페이지에서는 헤더 숨김
  const showHeader = !['/', '/register'].includes(router.pathname);
  const enableSocketProvider = router.pathname.startsWith('/chat');

  return (
    <ThemeProvider defaultTheme="dark">
      <AuthProvider>
        <AuthenticatedSocketProvider enabled={enableSocketProvider}>
          {showHeader && <ChatHeader />}
          <Component {...pageProps} />
          <ToastContainer />
        </AuthenticatedSocketProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default MyApp;
