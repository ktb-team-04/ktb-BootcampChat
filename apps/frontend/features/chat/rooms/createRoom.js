import api from '@/lib/api/client';

export const createRoomForCurrentUser = async ({ name, password }) => {
  const response = await api.post('/api/rooms', {
    name,
    password,
  });

  return response.data.data;
};
