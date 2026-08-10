import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '@/lib/api/client';
import { createRoomForCurrentUser } from '../createRoom';

vi.mock('@/lib/api/client', () => ({
  default: {
    post: vi.fn(),
  },
}));

describe('createRoomForCurrentUser', () => {
  beforeEach(() => {
    api.post.mockReset();
  });

  it('returns the created room without issuing a redundant join request', async () => {
    const room = { _id: 'room-1', name: 'load-test-room' };
    api.post.mockResolvedValue({ data: { success: true, data: room } });

    await expect(createRoomForCurrentUser({
      name: 'load-test-room',
      password: undefined,
    })).resolves.toEqual(room);

    expect(api.post).toHaveBeenCalledTimes(1);
    expect(api.post).toHaveBeenCalledWith('/api/rooms', {
      name: 'load-test-room',
      password: undefined,
    });
  });
});
