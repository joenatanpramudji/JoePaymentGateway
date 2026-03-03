#!/bin/bash
# ================================================================
# EC2 Instance Setup Script for JoePaymentGateway
# Run this ONCE on a fresh Amazon Linux 2023 / AL2 instance
# Usage: chmod +x ec2-setup.sh && ./ec2-setup.sh
# ================================================================

set -e
echo "=========================================="
echo "  JoePaymentGateway - EC2 Setup"
echo "=========================================="

# ── Step 1: Install Java 25 ─────────────────────────
echo "[1/4] Installing Java 25..."
# Amazon Corretto 25 (Amazon's OpenJDK distribution, optimized for EC2)
sudo yum install -y java-25-amazon-corretto-devel 2>/dev/null || {
    # Fallback: use Temurin if Corretto 25 isn't available yet
    echo "Corretto 25 not available, installing Temurin 25..."
    sudo yum install -y wget
    wget https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse -O /tmp/jdk25.tar.gz
    sudo mkdir -p /usr/lib/jvm
    sudo tar -xzf /tmp/jdk25.tar.gz -C /usr/lib/jvm/
    JDK_DIR=$(ls -d /usr/lib/jvm/jdk-25* | head -1)
    sudo alternatives --install /usr/bin/java java "$JDK_DIR/bin/java" 1
    sudo alternatives --install /usr/bin/javac javac "$JDK_DIR/bin/javac" 1
    rm /tmp/jdk25.tar.gz
}
java -version
echo ""

# ── Step 2: Create application directory ─────────────
echo "[2/4] Creating application directory..."
mkdir -p ~/app
echo ""

# ── Step 3: Install systemd service ─────────────────
echo "[3/4] Setting up systemd service..."
# Copy the service file (assumes deploy/paymentgateway.service is available)
if [ -f ~/app/paymentgateway.service ]; then
    sudo cp ~/app/paymentgateway.service /etc/systemd/system/paymentgateway.service
else
    echo "WARNING: paymentgateway.service not found in ~/app/"
    echo "  You need to copy it manually:"
    echo "  scp deploy/paymentgateway.service ec2-user@YOUR_EC2_IP:~/app/"
    echo "  Then run: sudo cp ~/app/paymentgateway.service /etc/systemd/system/"
fi

sudo systemctl daemon-reload
sudo systemctl enable paymentgateway
echo ""

# ── Step 4: Verify ──────────────────────────────────
echo "[4/4] Verifying setup..."
echo "  Java version: $(java -version 2>&1 | head -1)"
echo "  App directory: ~/app ($(ls ~/app/ 2>/dev/null | wc -l | tr -d ' ') files)"
echo "  Systemd service: $(sudo systemctl is-enabled paymentgateway 2>/dev/null || echo 'not installed')"
echo ""
echo "=========================================="
echo "  Setup complete!"
echo ""
echo "  Next steps:"
echo "  1. Edit /etc/systemd/system/paymentgateway.service"
echo "     - Set DB_URL to your RDS endpoint"
echo "     - Set DB_USERNAME / DB_PASSWORD"
echo "     - Set SECURITY_USER / SECURITY_PASSWORD"
echo "     - Set CRYPTO_BDK"
echo ""
echo "  2. Reload after editing:"
echo "     sudo systemctl daemon-reload"
echo ""
echo "  3. Deploy the JAR (CI/CD will do this, or manually):"
echo "     scp target/payment-gateway-1.0.0-SNAPSHOT.jar ec2-user@IP:~/app/app.jar"
echo ""
echo "  4. Start the application:"
echo "     sudo systemctl start paymentgateway"
echo "     sudo journalctl -u paymentgateway -f  (view logs)"
echo "=========================================="
