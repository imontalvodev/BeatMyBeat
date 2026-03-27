FROM node:20-bookworm-slim

WORKDIR /app

COPY package*.json /app/
RUN npm ci --omit=dev

COPY . /app

EXPOSE 3001

CMD ["npm", "run", "start"]
