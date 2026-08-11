import React from 'react';
import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import socketClient from '@/lib/socket/socketClient';
import ReadStatus from '../ReadStatus';

vi.mock('@/lib/socket/socketClient', () => ({
  default: {
    canSend: vi.fn(() => true),
    markMessagesAsRead: vi.fn(),
  },
}));

describe('ReadStatus', () => {
  it('does not mark optimistic messages as read', () => {
    const messageElement = document.createElement('div');
    const observe = vi.fn((target) => {
      expect(target).toBe(messageElement);
    });
    const disconnect = vi.fn();
    global.IntersectionObserver = vi.fn(function IntersectionObserver(callback) {
      callback([{ isIntersecting: true }]);
      return { observe, disconnect };
    });

    render(
      <ReadStatus
        messageType="text"
        participants={[{ id: 'user-1' }]}
        readers={[]}
        messageId="optimistic-1"
        messageRef={{ current: messageElement }}
        currentUserId="user-1"
      />
    );

    expect(socketClient.markMessagesAsRead).not.toHaveBeenCalled();
  });
});
