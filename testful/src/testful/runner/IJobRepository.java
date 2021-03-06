/*
 * TestFul - http://code.google.com/p/testful/
 * Copyright (C) 2010  Matteo Miraz
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package testful.runner;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Hosts jobs to be executed, and allows runners to put the result back.
 * @author matteo
 */
public interface IJobRepository extends Remote {

	public String getName() throws RemoteException;

	public <I extends Serializable, R extends Serializable> Job<I, R, ? extends IExecutor<I,R>> getJob() throws RemoteException;

	public void putResult(String key, Serializable result) throws RemoteException;

	public void putException(String key, Exception exc) throws RemoteException;
}
