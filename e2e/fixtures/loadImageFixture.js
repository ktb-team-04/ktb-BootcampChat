const path = require('path');

const LIGHTWEIGHT_IMAGE_SIZE = 50 * 1024;
const ONE_PIXEL_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
  'base64'
);

const createLightweightImageFixture = () => {
  const buffer = Buffer.alloc(LIGHTWEIGHT_IMAGE_SIZE);
  ONE_PIXEL_PNG.copy(buffer);

  return {
    name: 'load-test-50kb.png',
    mimeType: 'image/png',
    buffer,
  };
};

const getLoadImageFixture = () => {
  if (process.env.E2E_IMAGE_FIXTURE === 'original') {
    return path.resolve(__dirname, 'images/profile.jpg');
  }

  return createLightweightImageFixture();
};

module.exports = {
  LIGHTWEIGHT_IMAGE_SIZE,
  createLightweightImageFixture,
  getLoadImageFixture,
};
