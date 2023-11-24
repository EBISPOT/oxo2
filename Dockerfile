

FROM ubuntu:18.04

RUN apt update && apt install -y curl gpg

# node
RUN curl -sL https://deb.nodesource.com/setup_16.x | bash

# caddy
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
RUN curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list

# yarn
RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add -
RUN echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list

RUN apt update && apt install -y nodejs yarn caddy

RUN mkdir /opt/oxo2

WORKDIR /opt/oxo2

COPY package.json package-lock.json /opt/oxo2/
RUN npm install

COPY . /opt/oxo2/

RUN chmod +x /opt/oxo2/entrypoint.dockersh

CMD ["/opt/oxo2/entrypoint.dockersh"]

EXPOSE 8080
