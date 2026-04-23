const path = require('path');

const apiGatewayUrl =
  process.env.API_GATEWAY_URL ||
  (process.env.NODE_ENV === 'development' ? 'http://localhost:8080' : 'http://gateway');

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  outputFileTracingRoot: path.join(__dirname),
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${apiGatewayUrl}/api/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
