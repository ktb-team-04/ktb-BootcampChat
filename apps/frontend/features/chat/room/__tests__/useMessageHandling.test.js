import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Toast } from '@/components/Toast';
import socketClient from '@/lib/socket/socketClient';
import fileService from '@/services/fileService';
import { useMessageHandling } from '../useMessageHandling';

vi.mock('@/components/Toast', () => ({
  Toast: {
    error: vi.fn(),
  },
  default: () => null,
}));

vi.mock('@/services/fileService', () => ({
  default: {
    uploadFile: vi.fn(),
  },
}));

vi.mock('@/lib/socket/socketClient', () => ({
  default: {
    canSend: vi.fn(() => true),
    sendChatMessageAndWait: vi.fn(),
    fetchPreviousMessages: vi.fn(),
  },
}));

const roomId = 'room-1';

const currentUser = {
  token: 'token-1',
  sessionId: 'session-1',
};

describe('useMessageHandling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    socketClient.canSend.mockReturnValue(true);
  });

  it('sends trimmed text message through the subscribed room socket', async () => {
    const roomSocket = { connected: true };
    const socketRef = { current: roomSocket };
    const { result } = renderHook(() =>
      useMessageHandling(currentUser, roomId, vi.fn(), [], false, vi.fn(), socketRef)
    );

    await act(async () => {
      await result.current.handleMessageSubmit({ content: '  hello  ' });
    });

    expect(socketClient.sendChatMessageAndWait).toHaveBeenCalledWith(
      {
        room: 'room-1',
        type: 'text',
        content: 'hello',
      },
      roomSocket,
    );
  });

  it('adds an optimistic text message before the socket acknowledgement resolves', async () => {
    const roomSocket = { connected: true };
    const socketRef = { current: roomSocket };
    let resolveAck;
    socketClient.sendChatMessageAndWait.mockReturnValueOnce(
      new Promise(resolve => {
        resolveAck = resolve;
      })
    );
    let committedMessages = [];
    const setMessages = vi.fn(updater => {
      committedMessages = updater(committedMessages);
    });
    const { result } = renderHook(() =>
      useMessageHandling(
        currentUser,
        roomId,
        vi.fn(),
        [],
        false,
        vi.fn(),
        socketRef,
        true,
        setMessages,
      )
    );

    let submitPromise;
    await act(async () => {
      submitPromise = result.current.handleMessageSubmit({ type: 'text', content: '  visible now  ' });
      await Promise.resolve();
    });

    expect(committedMessages).toMatchObject([
      {
        room: roomId,
        type: 'text',
        content: 'visible now',
        _optimistic: true,
      },
    ]);

    resolveAck({});
    await act(async () => {
      await submitPromise;
    });
  });

  it('shows a connection error without emitting when disconnected', async () => {
    socketClient.canSend.mockReturnValue(false);
    const { result } = renderHook(() =>
      useMessageHandling(currentUser, roomId, vi.fn())
    );

    await act(async () => {
      await result.current.handleMessageSubmit({ content: 'hello' });
    });

    expect(socketClient.sendChatMessageAndWait).not.toHaveBeenCalled();
    expect(Toast.error).toHaveBeenCalledWith('채팅 서버와 연결이 끊어졌습니다.');
  });

  it('uploads files, sends file messages, and clears file preview state', async () => {
    const roomSocket = { connected: true };
    const socketRef = { current: roomSocket };
    fileService.uploadFile.mockResolvedValue({
      success: true,
      data: {
        file: {
          _id: 'file-1',
          filename: 'stored.pdf',
          originalname: 'sample.pdf',
          mimetype: 'application/pdf',
          size: 128,
        },
      },
    });
    const { result } = renderHook(() =>
      useMessageHandling(currentUser, roomId, vi.fn(), [], false, vi.fn(), socketRef)
    );

    await act(async () => {
      result.current.setFilePreview({ name: 'sample.pdf' });
      await result.current.handleMessageSubmit({
        type: 'file',
        content: 'attached',
        fileData: {
          file: { name: 'sample.pdf' },
        },
      });
    });

    expect(socketClient.sendChatMessageAndWait).toHaveBeenCalledWith(
      {
        room: 'room-1',
        type: 'file',
        content: 'attached',
        fileData: {
          _id: 'file-1',
          filename: 'stored.pdf',
          originalname: 'sample.pdf',
          mimetype: 'application/pdf',
          size: 128,
        },
      },
      roomSocket,
    );
    expect(result.current.filePreview).toBeNull();
    expect(result.current.uploadError).toBeNull();
  });

  it('removes only the failed optimistic text message when sending fails', async () => {
    const roomSocket = { connected: true };
    const socketRef = { current: roomSocket };
    const existingOptimistic = {
      _id: 'optimistic-existing',
      room: roomId,
      type: 'text',
      content: 'still pending',
      _optimistic: true,
    };
    let committedMessages = [existingOptimistic];
    const setMessages = vi.fn(updater => {
      committedMessages = updater(committedMessages);
    });
    socketClient.sendChatMessageAndWait.mockRejectedValueOnce(new Error('send failed'));

    const { result } = renderHook(() =>
      useMessageHandling(
        currentUser,
        roomId,
        vi.fn(),
        committedMessages,
        false,
        vi.fn(),
        socketRef,
        true,
        setMessages,
      )
    );

    await act(async () => {
      await result.current.handleMessageSubmit({ type: 'text', content: 'failed message' });
    });

    expect(committedMessages).toEqual([existingOptimistic]);
  });
});
