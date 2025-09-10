package metadata;

import java.util.ArrayList;
import java.util.List;

// Observer Interface
interface Observer
{
    void update(String message);
}

// Concrete Observer
class ConcreteObserver implements Observer
{
    private String name;

    public ConcreteObserver(String name)
    {
        this.name = name;
    }

    @Override
    public void update(String message)
    {
        System.out.println(name + " received message: " + message);
    }
}

// Subject Interface
interface Subject
{
    void registerObserver(Observer observer);
    void removeObserver(Observer observer);
    void notifyObservers(String message);
}

// Concrete Subject
class ConcreteSubject implements Subject
{
    private List<Observer> observers = new ArrayList<>();
    private String state;

    public void setState(String state)
    {
        this.state = state;
        notifyObservers("State changed to: " + state);
    }

    @Override
    public void registerObserver(Observer observer)
    {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer observer)
    {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(String message)
    {
        for (Observer observer : observers)
        {
            observer.update(message);
        }
    }
    
    /*
     * ConcreteSubject subject = new ConcreteSubject();
     *
     * ConcreteObserver observer1 = new ConcreteObserver("Observer 1");
     * ConcreteObserver observer2 = new ConcreteObserver("Observer 2");
     *
     * subject.registerObserver(observer1);
     * subject.registerObserver(observer2);
     *
     * subject.setState("New State");
     *
     * subject.removeObserver(observer1);
     * subject.setState("Another State");
     */

}