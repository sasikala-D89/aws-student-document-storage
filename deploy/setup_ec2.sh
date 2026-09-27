#!/usr/bin/env bash
# Run this once on a fresh Ubuntu 24.04 EC2 instance (as root / via sudo)
# to build and run the Student Documents Java app.
set -euo pipefail

APP_USER="studentdocs"
APP_DIR="/opt/studentdocs"
BUILD_DIR="/tmp/studentdocs-build"

echo "== Updating packages =="
apt-get update -y
apt-get upgrade -y

echo "== Installing Java 17, Maven, git, and the CloudWatch agent =="
apt-get install -y openjdk-17-jdk maven git curl

curl -s https://amazoncloudwatch-agent.s3.amazonaws.com/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb \
  -o /tmp/amazon-cloudwatch-agent.deb
dpkg -i /tmp/amazon-cloudwatch-agent.deb

echo "== Creating dedicated app user (no login shell) =="
id -u "$APP_USER" &>/dev/null || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$APP_USER"

echo "== Cloning and building the application =="
rm -rf "$BUILD_DIR"
git clone REPLACE_WITH_YOUR_GITHUB_REPO_URL "$BUILD_DIR"
cd "$BUILD_DIR"
mvn -q -DskipTests package

mkdir -p "$APP_DIR" /var/log/studentdocs data
cp target/studentdocs.jar "$APP_DIR/studentdocs.jar"
cp -r aws deploy "$APP_DIR/"
chown -R "$APP_USER":"$APP_USER" "$APP_DIR" /var/log/studentdocs

echo "== Installing systemd service =="
cp "$APP_DIR/deploy/studentdocs.service" /etc/systemd/system/studentdocs.service
systemctl daemon-reload
systemctl enable studentdocs
systemctl restart studentdocs

echo "== Starting CloudWatch agent =="
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s \
  -c file:"$APP_DIR/aws/cloudwatch-agent-config.json"

echo "Done. Check status with: systemctl status studentdocs"
echo "To redeploy after a code change: re-run this script, or build locally and scp the jar to $APP_DIR/studentdocs.jar, then 'systemctl restart studentdocs'."
