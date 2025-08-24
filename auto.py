import os
import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager

# Načítanie prihlasovacích údajov zo Secrets
username_value = os.environ["USERNAME"]
password_value = os.environ["PASSWORD"]

def automatizacia():
    print("🤖 Spúšťam automatizáciu...")

    # 1. Nastavenie ChromeOptions
    options = Options()
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-gpu')
    options.add_argument('--window-size=1920,1080')

    # 2. Vynútené stiahnutie presnej verzie ChromeDriveru
    driver_version = "139.0.7258.138"
    print(f"Používam ChromeDriver verziu {driver_version}")
    driver_path = ChromeDriverManager(driver_version).install()
    service = Service(ChromeDriverManager(version=driver_version).install())

    # 3. Spustenie prehliadača
    driver = webdriver.Chrome(service=service, options=options)

    try:
        # PRIHLÁSENIE
        driver.get("https://webbox.elko.sk/logindispatcher")
        WebDriverWait(driver, 15).until(EC.presence_of_element_located((By.ID, "username")))
        driver.find_element(By.ID, "username").send_keys(username_value)
        driver.find_element(By.ID, "password").send_keys(password_value)
        driver.find_element(By.XPATH, "//button[@type='submit']").click()

        # Čakanie na načítanie stavu
        WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CLASS_NAME, "label-danger")))
        status = driver.find_element(By.CLASS_NAME, "label-danger").text.strip()
        print(f"📋 Stav: {status}")

        # Ak “Práca / Príchod”, klik
        if status == "Práca / Príchod":
            btn = WebDriverWait(driver, 10).until(
                EC.element_to_be_clickable((By.XPATH, "//button[contains(text(),'Príchod')]"))
            )
            btn.click()
            print("✅ Kliknuté na Príchod")
            time.sleep(2)

        # Odhlásenie
        driver.get("https://webbox.elko.sk/logout")
        print("✅ Odhlásenie dokončené")

    finally:
        driver.quit()
        print("🔒 Prehliadač zatvorený")

if __name__ == "__main__":
    automatizacia()
