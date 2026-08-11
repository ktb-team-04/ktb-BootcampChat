import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import RoomsTable from '../RoomsTable';
import { CONNECTION_STATUS } from '../useServerConnection';

const room = (overrides) => ({
  _id: 'room-1',
  name: 'room',
  hasPassword: false,
  participants: [],
  recentMessageCount: 0,
  createdAt: '2026-08-10T00:00:00.000Z',
  ...overrides,
});

describe('RoomsTable', () => {
  it('exposes only public rooms to the E2E random-join selector', () => {
    const onJoinRoom = vi.fn();
    render(
      <RoomsTable
        rooms={[
          room({ _id: 'public-room', name: 'public' }),
          room({ _id: 'protected-room', name: 'protected', hasPassword: true }),
        ]}
        connectionStatus={CONNECTION_STATUS.CONNECTED}
        onJoinRoom={onJoinRoom}
      />
    );

    expect(screen.getAllByTestId('join-chat-room-button')).toHaveLength(1);
    expect(screen.getAllByTestId('join-protected-chat-room-button')).toHaveLength(1);

    fireEvent.click(screen.getByTestId('join-chat-room-button'));
    expect(onJoinRoom).toHaveBeenCalledWith('public-room');
  });
});
