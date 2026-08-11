import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ChatRoomsView from '../ChatRoomsView';
import { CONNECTION_STATUS } from '../useServerConnection';

const mocks = vi.hoisted(() => ({
  connectionStatus: 'checking',
  error: null,
  rooms: [],
  fetchRooms: vi.fn(() => Promise.resolve()),
  refreshRooms: vi.fn(() => Promise.resolve(true)),
  attemptConnection: vi.fn(() => Promise.resolve(true)),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 'user-1',
      token: 'token-1',
      sessionId: 'session-1',
    },
  }),
}));

vi.mock('../useServerConnection', async () => {
  const actual = await vi.importActual('../useServerConnection');
  return {
    ...actual,
    useServerConnection: () => ({
      connectionStatus: mocks.connectionStatus,
      setConnectionStatus: vi.fn(),
      retryCount: 0,
      setRetryCount: vi.fn(),
      isRetrying: false,
      setIsRetrying: vi.fn(),
      getRetryDelay: vi.fn(() => 1000),
      attemptConnection: mocks.attemptConnection,
    }),
  };
});

vi.mock('../useRoomList', () => ({
  useRoomList: () => ({
    rooms: mocks.rooms,
    setRooms: vi.fn(),
    error: mocks.error,
    loading: false,
    refreshing: false,
    joiningRoom: false,
    fetchRooms: mocks.fetchRooms,
    refreshRooms: mocks.refreshRooms,
    handleJoinRoom: vi.fn(),
  }),
}));

vi.mock('../useRoomsSocket', () => ({
  useRoomsSocket: vi.fn(),
}));

describe('ChatRoomsView', () => {
  beforeEach(() => {
    mocks.connectionStatus = CONNECTION_STATUS.CHECKING;
    mocks.error = null;
    mocks.rooms = [];
    mocks.fetchRooms.mockClear();
    mocks.refreshRooms.mockClear();
    mocks.attemptConnection.mockClear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not refetch rooms when connection status changes after the initial load starts', async () => {
    const { rerender } = render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await waitFor(() => {
      expect(mocks.fetchRooms).toHaveBeenCalledTimes(1);
    });

    mocks.connectionStatus = CONNECTION_STATUS.CONNECTED;
    rerender(<ChatRoomsView router={{ push: vi.fn() }} />);

    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(mocks.fetchRooms).toHaveBeenCalledTimes(1);
  });

  it('refreshes the room list on an interval while connected', async () => {
    mocks.connectionStatus = CONNECTION_STATUS.CONNECTED;
    vi.useFakeTimers();

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await vi.advanceTimersByTimeAsync(30000);

    expect(mocks.refreshRooms).toHaveBeenCalledWith({ silent: true });
  });

  it('does not auto refresh while the server connection is not established', async () => {
    mocks.connectionStatus = CONNECTION_STATUS.DISCONNECTED;
    vi.useFakeTimers();

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await vi.advanceTimersByTimeAsync(90000);

    expect(mocks.refreshRooms).not.toHaveBeenCalled();
  });

  it('retries the initial room fetch when the first attempt reports failure', async () => {
    vi.useFakeTimers();
    mocks.fetchRooms
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await Promise.resolve();

    expect(mocks.fetchRooms).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(3000);

    expect(mocks.fetchRooms).toHaveBeenCalledTimes(2);
  });

  it('keeps probing the server while the connection is in error state', async () => {
    mocks.connectionStatus = CONNECTION_STATUS.ERROR;
    vi.useFakeTimers();

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await vi.advanceTimersByTimeAsync(5000);

    expect(mocks.attemptConnection).toHaveBeenCalled();
  });

  it('catches up as soon as the tab becomes visible again', async () => {
    mocks.connectionStatus = CONNECTION_STATUS.CONNECTED;

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await waitFor(() => {
      expect(mocks.fetchRooms).toHaveBeenCalled();
    });

    document.dispatchEvent(new Event('visibilitychange'));

    expect(mocks.refreshRooms).toHaveBeenCalledWith({ silent: true });
  });

  it('refreshes the list when the refresh button is clicked', async () => {
    mocks.connectionStatus = CONNECTION_STATUS.CONNECTED;

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    fireEvent.click(await screen.findByTestId('refresh-rooms-button'));

    expect(mocks.refreshRooms).toHaveBeenCalledTimes(1);
    expect(mocks.refreshRooms).toHaveBeenCalledWith();
  });

  it('offers reconnect instead of refresh while an error is shown', async () => {
    mocks.connectionStatus = CONNECTION_STATUS.ERROR;
    mocks.error = { title: '연결 오류', message: '서버와 연결할 수 없습니다.', type: 'danger' };

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    await waitFor(() => {
      expect(screen.getByText('재연결')).toBeTruthy();
    });

    expect(screen.queryByTestId('refresh-rooms-button')).toBeNull();
  });

  it('marks the list as having no joinable rooms when every room is protected', () => {
    mocks.connectionStatus = CONNECTION_STATUS.CONNECTED;
    mocks.rooms = [{
      _id: 'protected-room',
      name: 'protected',
      hasPassword: true,
      participants: [],
      recentMessageCount: 0,
      createdAt: '2026-08-10T00:00:00.000Z',
    }];

    render(<ChatRoomsView router={{ push: vi.fn() }} />);

    expect(screen.getByTestId('rooms-empty')).toBeTruthy();
    expect(screen.queryByTestId('join-chat-room-button')).toBeNull();
    expect(screen.getByTestId('join-protected-chat-room-button')).toBeTruthy();
  });
});
