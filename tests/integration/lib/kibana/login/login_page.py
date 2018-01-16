'''
Created on Jan 16, 2018

@author: Liza Dayoub
'''

from lib.kibana.kibana_basepage import KibanaBasePage
from webium import Find
from webium.wait import wait
from selenium.webdriver.common.by import By


class LoginPage(KibanaBasePage):
    '''
    Kibana Login Page
    '''
    username_field = Find(by=By.CSS_SELECTOR, value='input[data-test-subj="loginUsername"]')
    password_field = Find(by=By.CSS_SELECTOR, value='input[data-test-subj="loginPassword"]')
    login_button = Find(by=By.CSS_SELECTOR, value='button[data-test-subj="loginSubmit"]')

    def __init__(self, **kwargs):
        super().__init__(**kwargs)

    def loaded(self):
        wait(lambda: self.is_element_present('username_field') is True)

    def enter_username(self, name):
        self.username_field.clear()
        self.username_field.send_keys(name)

    def enter_password(self, password):
        self.password_field.clear()
        self.password_field.send_keys(password)

    def click_submit(self):
        self.login_button.click()

    def login(self, username, password):
        self.open()
        self.loaded()
        self.enter_username(username)
        self.enter_password(password)
        self.click_submit()
