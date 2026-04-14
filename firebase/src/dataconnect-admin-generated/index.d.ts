import { ConnectorConfig, DataConnect, OperationOptions, ExecuteOperationResponse } from 'firebase-admin/data-connect';

export const connectorConfig: ConnectorConfig;

export type TimestampString = string;
export type UUIDString = string;
export type Int64String = string;
export type DateString = string;


export interface AllCompaniesData {
  companies: ({
    id: UUIDString;
    name: string;
    description?: string | null;
    website?: string | null;
    contactEmail?: string | null;
  } & Company_Key)[];
}

export interface AssetCategory_Key {
  assetId: UUIDString;
  categoryId: UUIDString;
  __typename?: 'AssetCategory_Key';
}

export interface AssetContact_Key {
  assetId: UUIDString;
  contactId: UUIDString;
  __typename?: 'AssetContact_Key';
}

export interface Asset_Key {
  id: UUIDString;
  __typename?: 'Asset_Key';
}

export interface Category_Key {
  id: UUIDString;
  __typename?: 'Category_Key';
}

export interface Company_Key {
  id: UUIDString;
  __typename?: 'Company_Key';
}

export interface Contact_Key {
  id: UUIDString;
  __typename?: 'Contact_Key';
}

export interface CreateCategoryData {
  category_insert: Category_Key;
}

export interface CreateCategoryVariables {
  name: string;
  description?: string | null;
  companyId: UUIDString;
}

export interface MyCompanyAssetsData {
  assets: ({
    id: UUIDString;
    name: string;
    type: string;
    renewalDate: DateString;
    provider: string;
    description?: string | null;
    cost?: number | null;
    isPublic?: boolean | null;
  } & Asset_Key)[];
}

export interface MyCompanyAssetsVariables {
  companyId: UUIDString;
}

export interface UpdateAssetData {
  asset_update?: Asset_Key | null;
}

export interface UpdateAssetVariables {
  id: UUIDString;
  name?: string | null;
  cost?: number | null;
}

export interface User_Key {
  id: UUIDString;
  __typename?: 'User_Key';
}

/** Generated Node Admin SDK operation action function for the 'AllCompanies' Query. Allow users to execute without passing in DataConnect. */
export function allCompanies(dc: DataConnect, options?: OperationOptions): Promise<ExecuteOperationResponse<AllCompaniesData>>;
/** Generated Node Admin SDK operation action function for the 'AllCompanies' Query. Allow users to pass in custom DataConnect instances. */
export function allCompanies(options?: OperationOptions): Promise<ExecuteOperationResponse<AllCompaniesData>>;

/** Generated Node Admin SDK operation action function for the 'MyCompanyAssets' Query. Allow users to execute without passing in DataConnect. */
export function myCompanyAssets(dc: DataConnect, vars: MyCompanyAssetsVariables, options?: OperationOptions): Promise<ExecuteOperationResponse<MyCompanyAssetsData>>;
/** Generated Node Admin SDK operation action function for the 'MyCompanyAssets' Query. Allow users to pass in custom DataConnect instances. */
export function myCompanyAssets(vars: MyCompanyAssetsVariables, options?: OperationOptions): Promise<ExecuteOperationResponse<MyCompanyAssetsData>>;

/** Generated Node Admin SDK operation action function for the 'CreateCategory' Mutation. Allow users to execute without passing in DataConnect. */
export function createCategory(dc: DataConnect, vars: CreateCategoryVariables, options?: OperationOptions): Promise<ExecuteOperationResponse<CreateCategoryData>>;
/** Generated Node Admin SDK operation action function for the 'CreateCategory' Mutation. Allow users to pass in custom DataConnect instances. */
export function createCategory(vars: CreateCategoryVariables, options?: OperationOptions): Promise<ExecuteOperationResponse<CreateCategoryData>>;

/** Generated Node Admin SDK operation action function for the 'UpdateAsset' Mutation. Allow users to execute without passing in DataConnect. */
export function updateAsset(dc: DataConnect, vars: UpdateAssetVariables, options?: OperationOptions): Promise<ExecuteOperationResponse<UpdateAssetData>>;
/** Generated Node Admin SDK operation action function for the 'UpdateAsset' Mutation. Allow users to pass in custom DataConnect instances. */
export function updateAsset(vars: UpdateAssetVariables, options?: OperationOptions): Promise<ExecuteOperationResponse<UpdateAssetData>>;

