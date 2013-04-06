package org.squirrelframework.foundation.fsm.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.squirrelframework.foundation.fsm.Action;
import org.squirrelframework.foundation.fsm.Actions;
import org.squirrelframework.foundation.fsm.ImmutableState;
import org.squirrelframework.foundation.fsm.ImmutableTransition;
import org.squirrelframework.foundation.fsm.MutableState;
import org.squirrelframework.foundation.fsm.MutableTransition;
import org.squirrelframework.foundation.fsm.StateContext;
import org.squirrelframework.foundation.fsm.StateMachine;
import org.squirrelframework.foundation.fsm.TransitionResult;
import org.squirrelframework.foundation.fsm.Visitor;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;

/**
 * The state model of the state machine implementation.
 * 
 * @author Henry.He
 *
 * @param <T> The type of implemented state machine
 * @param <S> The type of implemented state
 * @param <E> The type of implemented event
 * @param <C> The type of implemented context
 */
final class StateImpl<T extends StateMachine<T, S, E, C>, S, E, C> implements MutableState<T, S, E, C> {
    
    private static final Logger logger = LoggerFactory.getLogger(StateImpl.class);
    
    private final S stateId;
    
    private final Actions<T, S, E, C> entryActions = FSM.newActions();
    
    private final Actions<T, S, E, C> exitActions  = FSM.newActions();
    
    private final LinkedListMultimap<E, ImmutableTransition<T, S, E, C>> transitions = LinkedListMultimap.create();
    
    private MutableState<T, S, E, C> parentState;
    
    private List<MutableState<T, S, E, C>> childStates;
    
    private MutableState<T, S, E, C> childInitialState;

    private int level = 0;
    
    StateImpl(S stateId) {
        this.stateId = stateId;
    }
    
    @Override
    public S getStateId() {
        return stateId;
    }

    @Override
    public List<Action<T, S, E, C>> getEntryActions() {
        return entryActions.getAll();
    }

    @Override
    public List<Action<T, S, E, C>> getExitActions() {
        return exitActions.getAll();
    }

    @Override
    public List<ImmutableTransition<T, S, E, C>> getAllTransitions() {
        return Lists.newArrayList(transitions.values());
    }

    @Override
    public List<ImmutableTransition<T, S, E, C>> getTransitions(E event) {
        return Lists.newArrayList(transitions.get(event));
    }
    
    @Override
    public ImmutableState<T, S, E, C> getParentState() {
	    return parentState;
    }
    
    @Override
    public void setParentState(MutableState<T, S, E, C> parent) {
    	if(this==parent) {
    		throw new IllegalArgumentException("parent state cannot be state itself.");
    	}
		if(this.parentState==null) {
			this.parentState = parent;
			setLevel(this.parentState!=null ? this.parentState.getLevel()+1 : 1);
		} else {
			throw new RuntimeException("Cannot change state parent.");
		}
    }
    
    @Override
    public ImmutableState<T, S, E, C> getChildInitialState() {
	    return childInitialState;
    }

	@Override
    public void setChildInitialState(MutableState<T, S, E, C> childInitialState) {
	    if(this.childInitialState==null) {
	    	this.childInitialState = childInitialState;
	    } else {
	    	throw new RuntimeException("Cannot change child initial parent.");
	    }
    }

    @Override
    public void entry(StateContext<T, S, E, C> stateContext) {
        for(Action<T, S, E, C> entryAction : getEntryActions()) {
            entryAction.execute(null, stateId, stateContext.getEvent(), 
                    stateContext.getContext(), stateContext.getStateMachine());
        }
        logger.debug("State \""+stateId+"\" entry.");
    }
    
    @Override
    public ImmutableState<T, S, E, C> enterByHistory(StateContext<T, S, E, C> stateContext) {
    	return enterHistoryNone(stateContext);
    }
    
    @Override
    public ImmutableState<T, S, E, C> enterShallow(StateContext<T, S, E, C> stateContext) {
	    entry(stateContext);
	    return childInitialState!=null ? childInitialState.enterShallow(stateContext) : this;
    }
    
    /**
	 * Enters with history type = none.
	 * 
	 * @param stateContext
	 *            state context
	 * @return the entered state.
	 */
	private ImmutableState<T, S, E, C> enterHistoryNone(StateContext<T, S, E, C> stateContext) {
		return childInitialState != null ? childInitialState.enterShallow(stateContext) : this;
	}
    
    @Override
    public void exit(StateContext<T, S, E, C> stateContext) {
        for(Action<T, S, E, C> exitAction : getExitActions()) {
            exitAction.execute(stateId, null, stateContext.getEvent(), 
                    stateContext.getContext(), stateContext.getStateMachine());
        }
        logger.debug("State \""+stateId+"\" exit.");
    }

    @Override
    public MutableTransition<T, S, E, C> addTransitionOn(E event) {
        MutableTransition<T, S, E, C> newTransition = FSM.newTransition();
        newTransition.setSourceState(this);
        newTransition.setEvent(event);
        transitions.put(event, newTransition);
        return newTransition;
    }

    @Override
    public void addEntryAction(Action<T, S, E, C> newAction) {
        entryActions.add(newAction);
    }
    
    @Override
    public void addEntryActions(List<Action<T, S, E, C>> newActions) {
        entryActions.addAll(newActions);
    }

    @Override
    public void addExitAction(Action<T, S, E, C> newAction) {
        exitActions.add(newAction);
    }

    @Override
    public void addExitActions(List<Action<T, S, E, C>> newActions) {
        exitActions.addAll(newActions);
    }
    
    @Override
    public TransitionResult<T, S, E, C> internalFire(StateContext<T, S, E, C> stateContext) {
    	TransitionResult<T, S, E, C> result = TransitionResultImpl.notAccepted();
    	List<ImmutableTransition<T, S, E, C>> transitions = getTransitions(stateContext.getEvent());
        for(final ImmutableTransition<T, S, E, C> transition : transitions) {
        	result = transition.internalFire(stateContext);
        	if(result.isAccepted()) {
        		return result;
        	}
        }
        
        // fire to super state
        if(getParentState()!=null) {
        	logger.debug("Internal notify the same event to parent state");
        	result = getParentState().internalFire(stateContext);
        }
	    return result;
    }
    
    @Override
    public boolean isFinal() {
        return false;
    }
    
    @Override
    public void accept(Visitor<T, S, E, C> visitor) {
        visitor.visitOnEntry(this);
        for(ImmutableTransition<T, S, E, C> transition : getAllTransitions()) {
            transition.accept(visitor);
        }
        if(childStates!=null) {
        	for (ImmutableState<T, S, E, C> childState : childStates) {
        		childState.accept(visitor);
    		}
        }
        visitor.visitOnExit(this);
    }
    
    @Override
    public String toString() {
        return stateId.toString();
    }

	@Override
    public int getLevel() {
	    return level;
    }

	@Override
    public void setLevel(int level) {
	    this.level = level;
	    if(childStates!=null) {
	    	for (MutableState<T, S, E, C> state : childStates) {
				state.setLevel(this.level+1);
			}
	    }
    }

	@Override
    public void addChildState(MutableState<T, S, E, C> childState) {
		if(childState!=null) {
			if(childStates==null) {
		    	childStates = Lists.newArrayList();
		    }
			if(!childStates.contains(childState))
				childStates.add(childState);
		}
    }
}
