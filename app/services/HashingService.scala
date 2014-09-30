package services

import org.mindrot.jbcrypt.BCrypt

object HashingService {

  def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }
  
  def isMatch(password: String, passwordHash: String): Boolean = {
    BCrypt.checkpw(password, passwordHash)
  }
   
}