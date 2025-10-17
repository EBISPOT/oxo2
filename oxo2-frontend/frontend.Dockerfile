FROM ubuntu:22.04

RUN apt update && apt install -y curl gpg

# node
RUN curl -sL https://deb.nodesource.com/setup_18.x | bash

# caddy
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list


RUN apt update && apt install -y nodejs caddy

RUN mkdir /opt/oxo2-frontend


WORKDIR /opt/oxo2-frontend


COPY package.json package-lock.json /opt/oxo2-frontend/
RUN npm install

COPY . /opt/oxo2-frontend/

RUN chmod +x /opt/oxo2-frontend/entrypoint.dockersh

CMD ["/opt/oxo2-frontend/entrypoint.dockersh"]