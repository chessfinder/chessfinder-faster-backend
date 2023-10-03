# This Dockerfile file is used to test commands and then trnasfer them to the main .github/workflows/* files
# Doing that with the Dockerfile file is easier than doing it directlly with the .github/workflows/* files
# Since with the Dockerfile one can build and test the commands locally whcih is free and faster than doing it with the .github/workflows/* files

# ---- Build Stage ----
FROM amazonlinux:2 AS builder

# Update the system
RUN \
  yum update -y && \
  yum upgrade -y

# Install Zip and Unzip
RUN \
  yum install zip -y && \
  yum install unzip -y && \
  yum install tar -y && \
  yum install gzip -y && \
  yum install wget -y && \
  yum install which -y

# Installing toolchain
RUN \ 
  yum groupinstall "Development Tools" -y

# Install SDKMAN
RUN curl -s "https://get.sdkman.io" | bash

# Install GraalVM JDK
RUN \
  source "$HOME/.sdkman/bin/sdkman-init.sh" && \
  sdk install java 17.0.8-graal && \
  ln -s $HOME/.sdkman/candidates/java/current/bin/java /usr/bin/java

ENV JAVA_HOME=$HOME/.sdkman/candidates/java/current


#Install scala
# RUN \
#   curl -fL https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz | gzip -d > cs && chmod +x cs && ./cs setup -y && \
#   ln -s $HOME/.local/share/coursier/bin/scala /usr/local/bin/scala && \
#   ln -s $HOME/.local/share/coursier/bin/sbt /usr/local/bin/sbt

RUN \
  source "$HOME/.sdkman/bin/sdkman-init.sh" && \
  sdk install scala && \
  sdk install sbt && \
  ln -s $HOME/.sdkman/candidates/scala/current/bin/scala /usr/local/bin/scala && \
  ln -s $HOME/.sdkman/candidates/sbt/current/bin/sbt /usr/local/bin/sbt

ENV SCALA_HOME=$HOME/.sdkman/candidates/scala/current/bin/scala
ENV SBT_HOME=$HOME/.sdkman/candidates/sbt/current/bin/sbt

# Install Python and Pip
RUN \ 
  yum install python3 -y && \
  alternatives --install /usr/bin/python python /usr/bin/python2.7 1 && \
  alternatives --install /usr/bin/python python /usr/bin/python3 2 && \
  echo 1 | alternatives --config python && \
  ln -sf /usr/bin/pip3 /usr/bin/pip

# Installing Go
RUN \
  curl -O https://dl.google.com/go/go1.21.1.linux-amd64.tar.gz && \
  tar -C /usr/local -xzf go1.21.1.linux-amd64.tar.gz && \
  rm go1.21.1.linux-amd64.tar.gz && \
  ln -s /usr/local/go/bin/go /usr/bin/go


# ---- Final Stage ----
# FROM amazonlinux:2

# RUN \
#   yum update -y && \
#   yum install zip unzip tar gzip wget which -y && \
#   yum clean all

# RUN \ 
#   yum install python3 -y && \
#   alternatives --install /usr/bin/python python /usr/bin/python2.7 1 && \
#   alternatives --install /usr/bin/python python /usr/bin/python3 2 && \
#   echo 1 | alternatives --config python && \
#   ln -sf /usr/bin/pip3 /usr/bin/pip

# COPY --from=builder /root/.sdkman/candidates/java/current /opt/java
# ENV JAVA_HOME=/opt/java

# COPY --from=builder /root/.local/share/coursier/bin /opt/scala-tools
# ENV SCALA_HOME=/opt/scala-tools
# ENV SBT_HOME=/opt/scala-tools

# COPY --from=builder /usr/local/go /opt/go
# ENV GOROOT=/opt/go

# ENV PATH="$JAVA_HOME/bin:$SCALA_HOME:$SBT_HOME:$GOROOT/bin:$PATH"

# RUN \
#   ln -s $JAVA_HOME/bin/java /usr/bin/java && \
#   ln -s $SCALA_HOME/scala /usr/local/bin/scala && \
#   ln -s $SCALA_HOME/sbt /usr/local/bin/sbt && \
#   ln -s $GOROOT/bin/go /usr/bin/go

WORKDIR /app
