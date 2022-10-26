package io.eventdriven.shoppingcarts.entities;

public class EntityNotFoundException extends RuntimeException{
  public EntityNotFoundException(String message){
    super(message);
  }
}
