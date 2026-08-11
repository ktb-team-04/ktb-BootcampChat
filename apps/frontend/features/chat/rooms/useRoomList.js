import { useState, useCallback, useEffect, useRef } from 'react';
import axiosInstance from '@/services/axios';
import { CONNECTION_STATUS } from './useServerConnection';

const ROOM_JOIN_NAVIGATION_FALLBACK_MS = 1000;

const defaultHardNavigate = (path) => {
  if (typeof window !== 'undefined') {
    window.location.assign(path);
  }
};

export const useRoomList = ({
  currentUser,
  router,
  connectionStatus,
  setConnectionStatus,
  isRetrying,
  attemptConnection,
  hardNavigate = defaultHardNavigate,
}) => {
  const [rooms, setRooms] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [isInitialLoad, setIsInitialLoad] = useState(true);
  const [joiningRoom, setJoiningRoom] = useState(false);

  const isLoadingRef = useRef(false);
  const isJoiningRoomRef = useRef(false);
  const joinNavigationFallbackRef = useRef(null);
  const joinNavigationTargetRef = useRef(null);

  const clearJoinNavigationFallback = useCallback(() => {
    if (joinNavigationFallbackRef.current) {
      clearTimeout(joinNavigationFallbackRef.current);
      joinNavigationFallbackRef.current = null;
    }
    joinNavigationTargetRef.current = null;
  }, []);

  const scheduleJoinNavigationFallback = useCallback((roomPath) => {
    clearJoinNavigationFallback();
    joinNavigationTargetRef.current = roomPath;

    joinNavigationFallbackRef.current = setTimeout(() => {
      if (joinNavigationTargetRef.current !== roomPath) {
        return;
      }

      const currentPath = typeof window !== 'undefined' ? window.location.pathname : '';
      if (currentPath === roomPath) {
        joinNavigationFallbackRef.current = null;
        joinNavigationTargetRef.current = null;
        return;
      }

      hardNavigate(roomPath);
    }, ROOM_JOIN_NAVIGATION_FALLBACK_MS);
  }, [clearJoinNavigationFallback, hardNavigate]);

  const handleFetchError = useCallback((error) => {
    let errorMessage = '채팅방 목록을 불러오는데 실패했습니다.';
    let errorType = 'danger';
    let showRetry = !isRetrying;

    if (error.message === 'AUTH_EXPIRED') {
      errorMessage = '인증이 만료되었습니다. 다시 로그인해주세요.';
      errorType = 'danger';
      showRetry = false;

      setError({
        title: '인증 만료',
        message: errorMessage,
        type: errorType,
        showRetry,
      });

      setConnectionStatus(CONNECTION_STATUS.ERROR);
      return;
    }

    if (error.message === 'SERVER_UNREACHABLE') {
      errorMessage = '서버와 연결할 수 없습니다. 다시 시도해주세요.';
      errorType = 'warning';
      showRetry = true;
    }

    setError({
      title: '채팅방 목록 로드 실패',
      message: errorMessage,
      type: errorType,
      showRetry,
    });

    setConnectionStatus(CONNECTION_STATUS.ERROR);
  }, [isRetrying, setConnectionStatus]);

  const loadRooms = useCallback(async () => {
    await attemptConnection();

    const response = await axiosInstance.get('/api/rooms');

    if (!response?.data?.data) {
      throw new Error('INVALID_RESPONSE');
    }

    setRooms(response.data.data);
  }, [attemptConnection]);

  const fetchRooms = useCallback(async () => {
    if (!currentUser?.token || isLoadingRef.current) {
      return false;
    }

    try {
      isLoadingRef.current = true;

      setLoading(true);
      setError(null);

      await loadRooms();

      if (isInitialLoad) {
        setIsInitialLoad(false);
      }
      return true;
    } catch (error) {
      handleFetchError(error);
      return false;
    } finally {
      setLoading(false);
      isLoadingRef.current = false;
    }
  }, [currentUser, isInitialLoad, loadRooms, handleFetchError]);

  /**
   * 이미 그려진 목록을 유지한 채 다시 조회한다.
   * 자동 갱신(silent)은 실패해도 화면을 흔들지 않고 다음 주기를 기다린다.
   */
  const refreshRooms = useCallback(async ({ silent = false } = {}) => {
    if (!currentUser?.token || isLoadingRef.current) {
      return false;
    }

    try {
      isLoadingRef.current = true;
      setRefreshing(true);

      await loadRooms();
      setError(null);

      return true;
    } catch (error) {
      if (!silent) {
        setError({
          title: '채팅방 목록 갱신 실패',
          message: '목록을 갱신하지 못했습니다. 잠시 후 다시 시도해주세요.',
          type: 'warning',
          showRetry: false,
        });
      }

      return false;
    } finally {
      setRefreshing(false);
      isLoadingRef.current = false;
    }
  }, [currentUser, loadRooms]);

  const handleJoinRoom = useCallback(async (roomId) => {
    if (isJoiningRoomRef.current) {
      return;
    }

    if (connectionStatus !== CONNECTION_STATUS.CONNECTED) {
      setError({
        title: '채팅방 입장 실패',
        message: '서버와 연결이 끊어져 있습니다.',
        type: 'danger',
      });
      return;
    }

    isJoiningRoomRef.current = true;
    setJoiningRoom(true);
    let navigationStarted = false;

    try {
      const response = await axiosInstance.post(`/api/rooms/${roomId}/join`, {});

      if (response.data.success) {
        const roomPath = `/chat/${roomId}`;

        navigationStarted = true;
        scheduleJoinNavigationFallback(roomPath);

        await Promise.resolve(router.push(roomPath));
      }
    } catch (error) {
      navigationStarted = false;
      let errorMessage = '입장에 실패했습니다.';
      if (error.response?.status === 404) {
        errorMessage = '채팅방을 찾을 수 없습니다.';
      } else if (error.response?.status === 403) {
        errorMessage = '채팅방 입장 권한이 없습니다.';
      }

      setError({
        title: '채팅방 입장 실패',
        message: error.response?.data?.message || errorMessage,
        type: 'danger',
      });
      clearJoinNavigationFallback();
    } finally {
      if (!navigationStarted) {
        isJoiningRoomRef.current = false;
        setJoiningRoom(false);
      }
    }
  }, [connectionStatus, router, clearJoinNavigationFallback, scheduleJoinNavigationFallback]);

  useEffect(() => clearJoinNavigationFallback, [clearJoinNavigationFallback]);

  return {
    rooms,
    setRooms,
    error,
    setError,
    loading,
    refreshing,
    joiningRoom,
    fetchRooms,
    refreshRooms,
    handleJoinRoom,
  };
};

export default useRoomList;
