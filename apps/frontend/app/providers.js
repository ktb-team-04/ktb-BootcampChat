'use client';

import dynamic from 'next/dynamic';
import { usePathname } from 'next/navigation';
import { ThemeProvider } from '@vapor-ui/core';
import { useRouter } from 'next/navigation';
import ToastContainer from '@/components/Toast';
import { AuthProviderWithRouter, useAuth } from '@/contexts/AuthContext';

const SocketProvider = dynamic(
  () => import('@/lib/socket/SocketProvider').then((mod) => mod.SocketProvider),
  { ssr: false }
);

const AuthenticatedSocketProvider = ({ children, enabled }) => {
  const { user } = useAuth();

  if (!enabled) {
    return children;
  }

  return (
    <SocketProvider session={user}>
      {children}
    </SocketProvider>
  );
};

export default function AppProviders({ children }) {
  const router = useRouter();
  const pathname = usePathname();
  const enableSocketProvider = pathname?.startsWith('/chat') ?? false;

  return (
    <ThemeProvider defaultTheme="dark">
      <AuthProviderWithRouter router={router}>
        <AuthenticatedSocketProvider enabled={enableSocketProvider}>
          {children}
          <ToastContainer />
        </AuthenticatedSocketProvider>
      </AuthProviderWithRouter>
    </ThemeProvider>
  );
}
