# --- Build stage: produce the static bundle with Node 20 ---
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first (cached unless the lockfile changes).
COPY package.json package-lock.json ./
RUN npm ci

# Build the production bundle into /app/dist.
COPY . .
RUN npm run build

# --- Runtime stage: serve the static files with nginx ---
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
