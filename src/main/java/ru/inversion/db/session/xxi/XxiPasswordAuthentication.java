package ru.inversion.db.session.xxi;

import java.net.PasswordAuthentication;

/** */
public class XxiPasswordAuthentication {

   final private PasswordAuthentication authentication;
   final private boolean pseudoUser;

   public XxiPasswordAuthentication(PasswordAuthentication authentication, boolean pseudoUser) {
      this.authentication = authentication;
      this.pseudoUser = pseudoUser;
   }

   public XxiPasswordAuthentication(String userName, char[] password, boolean pseudoUser) {
      this.pseudoUser = pseudoUser;
      this.authentication = new PasswordAuthentication(userName,password);
   }

   /** */
   public String getUserName() {
      return authentication.getUserName();
   }

   /** */
   public char[] getPassword() {
      return authentication.getPassword();
   }

   /** */
   public boolean isPseudoUser() { return pseudoUser; }
}
